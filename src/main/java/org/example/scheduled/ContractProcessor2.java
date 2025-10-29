package org.example.scheduled;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.example.scheduled.module.ContractInfo;
import org.example.scheduled.module.ProcessResult;
import org.example.scheduled.service.CaseGrabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContractProcessor2 {

    private static final int MAX_RETRIES = 500;

    @Autowired
    private CaseGrabService caseGrabService;

    @Autowired
    private Executor contractProcessorExecutor;

    private static final int MAX_REQUESTS_PER_SECOND = 300;

    private final RateLimiter rateLimiter = RateLimiter.create(MAX_REQUESTS_PER_SECOND);
    private final ExecutorService sendExecutor = Executors.newFixedThreadPool(MAX_REQUESTS_PER_SECOND);
    private final ExecutorService callbackExecutor = Executors.newFixedThreadPool(
            100, // 线程池大小
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("callback-worker-" + counter.getAndIncrement());
                    thread.setDaemon(true); // 设置为守护线程
                    return thread;
                }
            }
    );;


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


    public CompletableFuture<List<ProcessResult>> processContractsAsync(List<ContractInfo> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        // 使用并行流处理合约，充分利用线程池ß
        List<CompletableFuture<ProcessResult>> futures = contracts.stream()
                .map(contract -> CompletableFuture.supplyAsync(() -> {
                    // 获取令牌，如果超过速率限制会阻塞
                    rateLimiter.acquire();
                    return processContractWithRetry(contract);
                }, contractProcessorExecutor))
                .collect(Collectors.toList());

        // allOf 收敛，不在池内阻塞等待；只在汇总时 join 已经完成的 future
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return all.thenApply(v ->
                futures.stream().map(CompletableFuture::join).collect(Collectors.toList())
        );
    }
    private ProcessResult processContractWithRetry(ContractInfo contract) {
        int attempts = 0;
        String lastResponse = null;
        long startTime = System.currentTimeMillis();

        while (attempts <= MAX_RETRIES) {
            attempts++;

            if (log.isDebugEnabled()) {
                log.debug("Attempt {} for contract {}", attempts, contract.getContractNo());
            }
            log.info("Sending attempt {} for {}", attempts, contract.getContractNo());


            lastResponse = caseGrabService.grabCase(contract.getSyskey(), contract.getContractNo(), contract.getUserId());

            if (isSuccessResponse(lastResponse)) {
                if (log.isInfoEnabled()) {
                    log.info("Processed [{}] in {}ms (attempt {}), response: {}",
                            contract.getContractNo(),
                            System.currentTimeMillis() - startTime,
                            attempts,
                            abbreviateResponse(lastResponse));
                }
                return new ProcessResult(contract.getContractNo(), true, lastResponse, attempts);
            }


            if (attempts < MAX_RETRIES) {
                long delay = calculateRetryDelay(attempts);
                if (delay > 0) {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("Waiting {}ms before retry [{}]", delay, contract.getContractNo());
                        }
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry interrupted for [{}]", contract.getContractNo());
                        return new ProcessResult(contract.getContractNo(), false, "Interrupted", attempts);
                    }
                }
            }
        }

        log.error("Failed after {} attempts for [{}] (total time: {}ms)",
                MAX_RETRIES,
                contract.getContractNo(),
                System.currentTimeMillis() - startTime);
        return new ProcessResult(contract.getContractNo(), false, lastResponse, attempts);
    }

    private ProcessResult sendRequestWithoutWaiting(ContractInfo contract) {
        int attempts = 0;
        String lastResponse = null;

        while (attempts <= MAX_RETRIES) {
            attempts++;

            try {
                // 异步发送请求（不等待响应）
                CompletableFuture<String> responseFuture = CompletableFuture.supplyAsync(
                        () -> caseGrabService.grabCase(
                                contract.getSyskey(),
                                contract.getContractNo(),
                                contract.getUserId()
                        ),
                        sendExecutor
                );

                // 非阻塞处理响应
                responseFuture.whenCompleteAsync((response, ex) -> {
                    if (ex != null) {
                        log.error("Request failed for {}", contract.getContractNo(), ex);
                    } else if (isSuccessResponse(response)) {
                        log.info("Request succeeded for {}", contract.getContractNo());
                    } else {
                        log.warn("Request rejected for {}", contract.getContractNo());
                    }
                }, callbackExecutor);

                // 立即返回"已发送"结果
                return new ProcessResult(contract.getContractNo(), true, "Request sent", attempts);

            } catch (Exception e) {
                log.error("Send attempt {} failed for {}", attempts, contract.getContractNo(), e);
                if (attempts >= MAX_RETRIES) {
                    return new ProcessResult(contract.getContractNo(), false, "Send failed", attempts);
                }
            }
        }

        return new ProcessResult(contract.getContractNo(), false, lastResponse, attempts);
    }
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
        return  response.contains("受理") || response.contains("不存在");
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
