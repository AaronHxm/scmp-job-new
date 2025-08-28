package org.example.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.example.scheduled.module.ProcessResult;
import org.springframework.stereotype.Service;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ResultAnalyzerService {

    public void analyzeResults(List<ProcessResult> results, String groupName) {
        // 1. 空列表检查（修正逻辑）
        if (results == null) {
            log.warn("!!! 结果列表为null，组别: {}", groupName);
            return;
        }
        if (results.isEmpty()) {
            log.warn("!!! 结果列表为空，组别: {}", groupName);
            return;
        }

        // 2. 分类统计
        Map<Boolean, List<ProcessResult>> grouped = results.stream()
                .collect(Collectors.partitioningBy(ProcessResult::isSuccess));

        long successCount = grouped.get(true).size();
        long failureCount = grouped.get(false).size();
        long totalCount = results.size();

        // 3. 结果分析报告（修正格式字符串）
        log.info("\n=== {} 详细分析 ===", groupName);
        log.info("总计处理: {}", totalCount);
        log.info("成功数量: {} ({}%)",
                successCount,
                String.format("%.1f", totalCount > 0 ? 100.0 * successCount / totalCount : 0));
        log.info("失败数量: {} ({}%)",
                failureCount,
                String.format("%.1f", totalCount > 0 ? 100.0 * failureCount / totalCount : 0));

        // 4. 失败详情（去重显示）
        if (failureCount > 0) {
            log.warn("\n失败明细（前20条不重复记录）:");
            grouped.get(false).stream()
                    .collect(Collectors.toMap(
                            ProcessResult::getContractNo,
                            r -> r,
                            (existing, replacement) -> existing)) // 去重逻辑
                    .values().stream()
                    .limit(20)
                    .forEach(r -> log.warn("合约: {}, 尝试次数: {}, 原因: {}{}",
                            r.getContractNo(),
                            r.getAttempts(),
                            abbreviateMessage(r.getMessage()),
                            r.getAttempts() > 1 ? " (经过重试)" : ""));

            if (failureCount > 20) {
                log.warn("...（省略{}条失败记录）", failureCount - 20);
            }
        }

        // 5. 尝试次数分析（修正格式）
        IntSummaryStatistics attemptsStats = results.stream()
                .mapToInt(ProcessResult::getAttempts)
                .summaryStatistics();

        log.info("\n尝试次数分析:");
        log.info("平均: {}", String.format("%.2f", attemptsStats.getAverage()));
        log.info("最大: {}", attemptsStats.getMax());
        log.info("最小: {}", attemptsStats.getMin());

        // 优化分布显示格式
        Map<Integer, Long> attemptsDistribution = results.stream()
                .collect(Collectors.groupingBy(
                        ProcessResult::getAttempts,
                        Collectors.counting()));

        String distributionStr = attemptsDistribution.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> String.format("%d次=%d", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));

        log.info("分布: {}", distributionStr);
    }

    private String abbreviateMessage(String msg) {
        if (msg == null) return "null";
        return msg.length() > 80 ? msg.substring(0, 80) + "..." : msg;
    }
}
