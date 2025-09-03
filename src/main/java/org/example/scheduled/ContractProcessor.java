package org.example.scheduled;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.example.scheduled.module.ContractInfo;
import org.example.scheduled.module.ProcessResult;
import org.example.scheduled.service.CaseGrabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ContractProcessor {

    private static final int MAX_RETRIES = 3;

    /** 每个账号允许的最大 RPS（每秒请求数） */
    private static final int PER_USER_MAX_RPS = 2; // 你的约束：2单/秒
    /** 额外的“每个小任务之间”的休眠（毫秒）。如果你确实要 200 秒，这里改成 200_000L。*/
    private static final long EXTRA_TASK_PAUSE_MS = 200L;

    // 每个 userId 一个独立的 limiter
    private final ConcurrentHashMap<String, RateLimiter> userRateLimiters = new ConcurrentHashMap<>();
    // 全局：每个 userId 一个单线程 Executor
    private final ConcurrentHashMap<String, ExecutorService> executors = new ConcurrentHashMap<>();
    // 每个账号的速率限制器，1秒1单
    private final ConcurrentHashMap<String, RateLimiter> accountRateLimiters = new ConcurrentHashMap<>();


    @Autowired
    private CaseGrabService caseGrabService;

    private int SLEEP_TIME = 800;

    // 每个账号单独的串行执行器
    private final ConcurrentHashMap<String, ExecutorService> accountExecutors = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, UserProcessor> USER_PROCESSORS = new ConcurrentHashMap<>();
    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);


    public ContractProcessor(CaseGrabService caseGrabService) {
        this.caseGrabService = caseGrabService;
    }
    /**
     * 项目启动时初始化 - 为每个userId创建守护线程
     */
    public void initializeUserProcessors(List<String> userIds) {
        if (isInitialized.compareAndSet(false, true)) {
            for (String userId : userIds) {
                UserProcessor processor = new UserProcessor(userId);
                USER_PROCESSORS.put(userId, processor);
                processor.start(); // 启动守护线程
            }
            log.info("Initialized {} user processor threads", userIds.size());
        }
    }

    /**
     * 添加合约到指定用户的处理队列
     */
    public void addContractToUser(String userId, ContractInfo contract) {
        UserProcessor processor = USER_PROCESSORS.get(userId);
        if (processor != null) {
            processor.addContract(contract);
        } else {
            log.warn("No processor found for user: {}", userId);
        }
    }

    /**
     * 停止所有用户处理器
     */
    public void shutdownAllProcessors() {
        USER_PROCESSORS.values().forEach(UserProcessor::stop);
        log.info("All user processors stopped");
    }

    /**
     * 单个用户处理器（守护线程）
     */
    private class UserProcessor {
        private final String userId;
        private final BlockingQueue<ContractInfo> contractQueue;
        private volatile boolean running;
        private Thread processorThread;

        public UserProcessor(String userId) {
            this.userId = userId;
            this.contractQueue = new LinkedBlockingQueue<>();
            this.running = false;
        }

        /**
         * 启动守护线程 - 不断循环处理自己的任务列表
         */
        public void start() {
            if (running) return;

            running = true;
            processorThread = new Thread(() -> {
                log.info("User processor started for: {}", userId);

                // 不停循环，即使没有数据也空转运行
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        // 检查队列中是否有合约
                        ContractInfo contract = contractQueue.poll();

                        if (contract != null) {
                            // 有数据：处理合约
                            ProcessResult result = processContractWithRetry(contract);

                            // 成功后严格休眠500ms，确保每秒最多2次
                            Thread.sleep(SLEEP_TIME);
                        } else {
                            // 无数据：空转休眠，避免CPU占用过高
                            Thread.sleep(100);
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Error in user processor {}", userId, e);
                    }
                }

                log.info("User processor stopped for: {}", userId);
            }, "UserProcessor-" + userId);

            processorThread.setDaemon(true); // 设置为守护线程
            processorThread.start();
        }

        /**
         * 处理下一个合约
         */
        private void processNextContract() throws InterruptedException {
            // 从队列获取合约（阻塞等待）
            ContractInfo contract = contractQueue.take();

            if (contract != null) {
                // 调用processContractWithRetry处理合约
                ProcessResult result = processContractWithRetry(contract);

                log.info("User {} processed contract {}: success={}, attempts={}",
                        userId, contract.getContractNo(), result.isSuccess(), result.getAttempts());

                // 成功后严格休眠200ms，确保速率限制
                Thread.sleep(200);
            }
        }

        /**
         * 添加合约到队列
         */
        public void addContract(ContractInfo contract) {
            if (contractQueue.offer(contract)) {
                log.debug("Added contract {} to user {} queue", contract.getContractNo(), userId);
            }
        }

        /**
         * 停止处理器
         */
        public void stop() {
            running = false;
            if (processorThread != null) {
                processorThread.interrupt();
            }
        }

        public int getQueueSize() {
            return contractQueue.size();
        }
    }

    /**
     * 合约处理逻辑（带重试）
     */
    private ProcessResult processContractWithRetry(ContractInfo contract) {
        int attempts = 0;
        String lastResponse = null;

        while (attempts <= MAX_RETRIES) {
            attempts++;

            try {
                lastResponse = caseGrabService.grabCase(contract.getSyskey(), contract.getContractNo(), contract.getUserId());

                if (isSuccessResponse(lastResponse)) {
                    return new ProcessResult(contract.getContractNo(), true, lastResponse, attempts);
                }

                // 重试等待
                if (attempts < MAX_RETRIES) {
                    Thread.sleep(SLEEP_TIME);
                    addContractToUser(contract.getUserId(), contract);
                }

            } catch (Exception e) {
                log.error("Attempt {} failed for contract {}", attempts, contract.getContractNo(), e);
                if (attempts >= MAX_RETRIES) {
                    return new ProcessResult(contract.getContractNo(), false, "Exception", attempts);
                }
            }
        }

        return new ProcessResult(contract.getContractNo(), false, lastResponse, attempts);
    }

//    @Async("contractProcessorExecutor")
//    public CompletableFuture<List<ProcessResult>> processContractsAsync(List<ContractInfo> contracts) {
//        log.info("Async processing contracts, count: {}", contracts.size());
//
//        // 使用并行流处理合约，充分利用线程池
//        List<CompletableFuture<ProcessResult>> futures = contracts.parallelStream()
//                .map(contract -> CompletableFuture.supplyAsync(() ->
//                        processContractWithRetry(contract), contractProcessorExecutor))
//                .collect(Collectors.toList());
//
//        // 等待所有任务完成
//        // 等待所有任务完成
//        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
//                .thenApply(v -> futures.stream()
//                        .map(CompletableFuture::join)
//                        .collect(Collectors.toList()));
//    }

//
//    public CompletableFuture<List<ProcessResult>> processContractsAsync(List<ContractInfo> contracts) {
//        if (contracts == null || contracts.isEmpty()) {
//            return CompletableFuture.completedFuture(Collections.emptyList());
//        }
//        // 使用并行流处理合约，充分利用线程池ß
//        List<CompletableFuture<ProcessResult>> futures = contracts.stream()
//                .map(contract -> CompletableFuture.supplyAsync(() ->
//                        processContractWithRetry(contract), contractProcessorExecutor))
//                .collect(Collectors.toList());
//
//        // allOf 收敛，不在池内阻塞等待；只在汇总时 join 已经完成的 future
//        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
//        return all.thenApply(v ->
//                futures.stream().map(CompletableFuture::join).collect(Collectors.toList())
//        );
//    }
//
//
//    private ProcessResult processContractWithRetry(ContractInfo contract) {
//        int attempts = 0;
//        String lastResponse = null;
//        long startTime = System.currentTimeMillis();
//
//        while (attempts <= MAX_RETRIES) {
//            attempts++;
//
//            if (log.isDebugEnabled()) {
//                log.debug("Attempt {} for contract {}", attempts, contract.getContractNo());
//            }
//            try {
//                Thread.sleep(500);
//            }catch (InterruptedException e){}
//
//            lastResponse = caseGrabService.grabCase(contract.getSyskey(), contract.getContractNo());
//
//            if (isSuccessResponse(lastResponse)) {
//                if (log.isInfoEnabled()) {
//                    log.info("Processed [{}] in {}ms (attempt {}), response: {}",
//                            contract.getContractNo(),
//                            System.currentTimeMillis() - startTime,
//                            attempts,
//                            abbreviateResponse(lastResponse));
//                }
//                return new ProcessResult(contract.getContractNo(), true, lastResponse, attempts);
//            }
//
//
//            if (attempts < MAX_RETRIES) {
//                long delay = calculateRetryDelay(attempts);
//                if (delay > 0) {
//                    try {
//                        if (log.isDebugEnabled()) {
//                            log.debug("Waiting {}ms before retry [{}]", delay, contract.getContractNo());
//                        }
//                        Thread.sleep(delay);
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                        log.error("Retry interrupted for [{}]", contract.getContractNo());
//                        return new ProcessResult(contract.getContractNo(), false, "Interrupted", attempts);
//                    }
//                }
//            }
//        }
//
//        log.error("Failed after {} attempts for [{}] (total time: {}ms)",
//                MAX_RETRIES,
//                contract.getContractNo(),
//                System.currentTimeMillis() - startTime);
//        return new ProcessResult(contract.getContractNo(), false, lastResponse, attempts);
//    }

    // 优化后的辅助方法
    private long calculateRetryDelay(int attempt) {
        // 动态调整的重试策略：
        // - 第一次立即重试（delay=0）
        // - 后续采用短延迟（避免过长等待）
        return attempt == 1 ? 0 : Math.min(200, (long) (50 * Math.pow(2, attempt)));
    }

    private String abbreviateResponse(String response) {
        if (response == null) return "null";
        return response.length() > 100 ? response.substring(0, 100) + "..." : response;
    }

    private boolean isSuccessResponse(String response) {
        // 这里根据实际业务逻辑实现
        // 示例：假设返回的JSON中包含 success:true 表示成功
        return  response.contains("受理") ;
    }

    private ProcessResult buildSuccessResult(ContractInfo contract, String response, int attempts) {
        return new ProcessResult(
                contract.getContractNo(),
                true,
                response,
                attempts
        );
    }

    private ProcessResult buildFailureResult(ContractInfo contract, String error, int attempts) {
        return new ProcessResult(
                contract.getContractNo(),
                false,
                error,
                attempts
        );
    }
}
