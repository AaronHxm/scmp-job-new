import org.example.scheduled.module.ContractInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestA {
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
            contracts.add(contractInfo );
        }
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
            contracts.add(contractInfo );        }
        return contracts;
    }
}
