package org.example.scheduled;


import lombok.extern.slf4j.Slf4j;
import org.example.scheduled.module.ContractInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ContractGrouper {
    public Map<String, List<ContractInfo>> groupContracts(List<ContractInfo> contracts) {
        log.info("开始分组合约，总数: {}", contracts.size());

        Map<Boolean, List<ContractInfo>> partitioned = contracts.stream()
                .collect(Collectors.partitioningBy(this::isLongTermContract));

        Map<String, List<ContractInfo>> result = Map.of(
                "shortTerm", partitioned.get(false),
                "longTerm", partitioned.get(true)
        );

        log.info("分组完成 - 短期(0-120天): {}个, 长期(120-360天): {}个",
                result.get("shortTerm").size(), result.get("longTerm").size());

        return result;
    }

    private boolean isLongTermContract(ContractInfo contract) {
        return contract.getTotalODDays() > 120 && contract.getTotalODDays() <= 360;
    }
}
