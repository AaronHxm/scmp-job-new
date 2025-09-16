package org.example.scheduled;


import lombok.extern.slf4j.Slf4j;
import org.example.scheduled.module.ContractInfo;
import org.example.scheduled.module.ProcessResult;
import org.example.scheduled.module.QueryResponse;
import org.example.scheduled.service.CollectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class NonBlockingScheduledTasks2 implements CommandLineRunner {

    @Autowired
    private ContractFilter filterService;

    @Autowired
    private ContractGrouper grouperService;

    @Autowired
    private ContractProcessor2 processorService2;

    @Autowired
    private ResultAnalyzerService analyzerService;
    @Autowired
    private CollectionService collectionService;
    @Autowired
    private Executor bigTaskExecutor;

    private volatile List<ContractInfo> filtered = new ArrayList<>();

    /**
     * 120-36-ABT
     */
    private volatile List<ContractInfo> rule1list = new ArrayList<>();
    @Scheduled(cron = "0 */10 * * * ?")
    public void taskWithCronExpression() {
        USER_IDS.stream()
                .forEach(v ->{
                    try {
                        QueryResponse<ContractInfo> contractInfoQueryResponse = collectionService.fetchPreGrabCases(1, v);
                        List<ContractInfo> rows = contractInfoQueryResponse.getRows();
                        log.info("到期时间：{},当前用户:{},数量:{}",new Date(),v,rows.size());
                    }catch (Exception ex){

                    }
                });
    }

    /**
     * 360-
     */

    private volatile List<ContractInfo> rule2list = new ArrayList<>();

    // 全局静态用户ID列表（不可变）
    private static final List<String> USER_IDS = List.of(
            "1aca0637a4774f14a8b6f16cf85642b3", "f154bcb4663e492fb2eff13e4e260c3f", "676b91276b0244c19c901b33cbef8a4a",  "1b84d25bec0e4509b4e8ed8030339b8c"
    );

    private static final int DEFAULT_USER_INDEX = 1;
    /**
     * 定时任务A：每天8点执行 - 单线程查询
     */
    @Scheduled(cron = "0 30 8 * * ?")
    public void taskA() {
        log.info("开始抢单");

        // 3. 记录开始时间
        long startTime = System.currentTimeMillis();

        // 4. 异步处理
//
//// 两个大任务，本质上就是发起两个 future，它们内部会把小任务丢到统一的池里
        CompletableFuture<List<ProcessResult>> shortFuture =   CompletableFuture.supplyAsync(
                () -> processorService2.processContractsAsync(rule2list).join(),
                bigTaskExecutor);

//
        CompletableFuture<List<ProcessResult>> longFuture =   CompletableFuture.supplyAsync(
                () -> processorService2.processContractsAsync(rule1list).join(),
                bigTaskExecutor);
//
//// 等待并合并
        CompletableFuture<List<ProcessResult>> allResults = shortFuture.thenCombine(
                longFuture,
                (a, b) -> {
                    List<ProcessResult> merged = new ArrayList<>(a.size() + b.size());
                    merged.addAll(a);
                    merged.addAll(b);
                    return merged;
                }
        );

        List<ProcessResult> results = allResults.join();
        // 5. 等待所有任务完成
        CompletableFuture.allOf(shortFuture, longFuture).join();
        // 6. 计算总耗时
        long totalTimeMillis = System.currentTimeMillis() - startTime;
        log.info("All done, cost={}ms, totalResults={}", totalTimeMillis, results.size());

//        // 7. 分析结果
//        try {
//            analyzerService.analyzeResults(shortFuture.get(), "Short Term");
//            analyzerService.analyzeResults(longFuture.get(), "Long Term");
//        } catch (Exception e) {
//            log.error("Error analyzing results", e);
//        }
//        log.info("Total time: {} ms", totalTimeMillis);
        log.info("Total size: {} ", filtered.size());


    }

    @Override
    public void run(String... args) throws Exception {

        QueryResponse<ContractInfo> listResponse = collectionService.fetchAllPreGrabCases();

        List<ContractInfo> contracts = listResponse.getRows().stream()
                .collect(Collectors.toMap(
                        ContractInfo::getContractNo,  // key = contractNo
                        Function.identity(),     // value = 对象本身
                        (existing, replacement) -> existing  // 如果 key 冲突，保留第一个
                ))
                .values()                   // 获取去重后的值
                .stream()                   // 转回 Stream
                .collect(Collectors.toList());  // 转回 List;;
        log.info("Received request to process {} contracts", contracts.size());

        contracts = contracts.stream().filter(v ->v.getTotalODDays() <=360).collect(Collectors.toList());;


        assignUserIds(contracts);
        log.info("[TaskA] 查询完成， ，获取{}条数据",  contracts.size());
        /**
         *  进行分组
         */
//        contracts = ContractDistributor.distributeContracts(USER_IDS,contracts,DEFAULT_USER_INDEX);

        // 1. 过滤合约
        filtered = filterService.filterContracts(contracts);

        rule1list = filtered;
        Collections.shuffle(rule1list);

        rule2list = contracts.stream().filter(v ->v.getTotalODDays() <=360).collect(Collectors.toList());;
        Collections.shuffle(rule2list);

        long start = System.currentTimeMillis();
        log.info("原始数据=========120到360,ABT===========================");

        rule1list.forEach(v -> {

            log.info("合同编号：【{}】,姓名:【{}】,天数：【{}】", v.getContractNo(), v.getCustomerName(), v.getTotalODDays());

        });
        log.info("======================360=====================================");
        rule2list.forEach(v -> {

            log.info("合同编号：【{}】,姓名:【{}】,天数：【{}】", v.getContractNo(), v.getCustomerName(), v.getTotalODDays());

        });
        log.info("[TaskA] 查询完成，120到160,ABT，耗时{}ms，获取{}条数据",
                System.currentTimeMillis() - start, rule1list.size());

        log.info("[TaskA] 查询完成，360,耗时{}ms，获取{}条数据",
                System.currentTimeMillis() - start, rule2list.size());




//        taskA();

    }

    // 设置用户id
    public static void assignUserIds(List<ContractInfo> contracts) {
        for (int i = 0; i < contracts.size(); i++) {
            String userId = USER_IDS.get(i % USER_IDS.size()); // 取余实现轮流分配
            contracts.get(i).setUserId(userId);
        }
    }


    private static final Random random = new Random();

    // 生成A开头的短期合约（0-120天）
    public static List<ContractInfo> generateShortTermContracts(int count) {
        List<ContractInfo> contracts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String contractNo = "A" + String.format("%04d", i); // A0001, A0002,...A0500
            int totalODDays = random.nextInt(121); // 0-120随机天数
            ContractInfo contractInfo = new ContractInfo();
            contractInfo.setContractNo(contractNo);
            contractInfo.setTotalODDays(totalODDays);
            contracts.add(contractInfo);
        }
        assignUserIds(contracts);
        return contracts;
    }

    // 生成B开头的长期合约（121-360天）
    public static List<ContractInfo> generateLongTermContracts(int count) {
        List<ContractInfo> contracts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String contractNo = "B" + String.format("%04d", i); // B0001, B0002,...B0500
            int totalODDays = 121 + random.nextInt(240); // 121-360随机天数
            ContractInfo contractInfo = new ContractInfo();

            contractInfo.setContractNo(contractNo);
            contractInfo.setTotalODDays(totalODDays);
            contracts.add(contractInfo);
        }

        assignUserIds(contracts);
        return contracts;
    }
}
