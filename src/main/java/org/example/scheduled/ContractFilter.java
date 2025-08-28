package org.example.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.example.scheduled.module.ContractInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ContractFilter {

    public  List<ContractInfo> filterContracts(List<ContractInfo> originalList) {
        log.info("开始过滤合约列表，原始数量: {}", originalList.size());

        List<ContractInfo> filtered = originalList.stream()
                .filter(this::isValidContract)
                .collect(Collectors.toList());

        log.info("过滤完成，有效合约数量: {}", filtered.size());
        return filtered;
    }

    private boolean isValidContract(ContractInfo contract) {
        if (contract.getContractNo() == null || contract.getContractNo().length() < 3) {
            log.debug("合约号无效: {}", contract.getContractNo());
            return false;
        }

        char thirdChar = contract.getContractNo().charAt(2);
        boolean valid = thirdChar == 'A' || thirdChar == 'B' || thirdChar == 'T';
        boolean validDay = contract.getTotalODDays() <360;

        if (!valid) {
            log.debug("合约号第三个字符不符合要求: {}", contract.getContractNo());
        }

        if (!validDay) {
            log.debug("合约号天数不符合要求: {},天数{}", contract.getContractNo(),contract.getTotalODDays());
        }

        return valid && validDay;
    }
}
