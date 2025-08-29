package org.example.scheduled;

import org.example.scheduled.module.ContractInfo;

import java.util.ArrayList;
import java.util.List;

public class ContractDistributor {

    public static List<ContractInfo> distributeContracts(
            List<String> userIds,
            List<ContractInfo> contractInfos,
            int index) {

        // 处理边界条件：userIds为空或contractInfos为空
        if (userIds == null || userIds.isEmpty() || contractInfos == null || contractInfos.isEmpty()) {
            return new ArrayList<>();
        }

        // 处理index边界条件
        int userIdCount = userIds.size();
        int adjustedIndex = adjustIndex(index, userIdCount);

        // 获取对应的userId
        String targetUserId = userIds.get(adjustedIndex - 1); // 转换为0-based索引

        // 计算每部分的大小
        int totalContracts = contractInfos.size();
        int baseSize = totalContracts / userIdCount;
        int remainder = totalContracts % userIdCount;

        // 计算当前部分的起始和结束索引
        int startIndex = (adjustedIndex - 1) * baseSize + Math.min(adjustedIndex - 1, remainder);
        int endIndex = startIndex + baseSize + (adjustedIndex <= remainder ? 1 : 0);

        // 获取子列表
        List<ContractInfo> subList = new ArrayList<>(contractInfos.subList(startIndex, endIndex));

        // 设置userId
        for (ContractInfo contract : subList) {
            contract.setUserId(targetUserId);
        }

        return subList;
    }

    private static int adjustIndex(int index, int userIdCount) {
        if (index < 1 || index > userIdCount) {
            return 1; // 超出范围则返回第一个
        }
        return index;
    }
}
