package org.example.scheduled.module;


import lombok.Data;

import java.util.Date;

@Data
public class ContractInfo {
    private Long syskey;                        // 系统主键
    private String contractNo;                  // 合同编号
    private String accountType;                // 账户类型
    private String customerName;               // 客户姓名
    private String provinceName;               // 省份名称
    private String cityName;                   // 城市名称
    private String dlrSimpleName;              // 经销商简称
    private String dlrProvinceName;            // 经销商所在省
    private String areaName;                   // 区域名称
    private String brand;                      // 品牌
    private String assetDsc;                   // 资产描述
    private String businessType;               // 业务类型
    private Integer totalODDays;                // 总逾期天数
    private Integer delinquencyTerm;           // 逾期期数
    private String odrentalAmt;            // 逾期租金金额
    private String totalODAmt;             // 总逾期金额
    private Integer dcaTranTimes;              // 催收流转次数
    private Integer relatedContractNum;        // 关联合同数量
    private String loseContactFlag;            // 失联标志
    private String dcaType;                    // 催收类型
    private String customerStatus;             // 客户状态
    private String carStatus;                  // 车辆状态
    private String prevCompName;               // 前手公司名称
    private String compId;                     // 公司ID
    private String compName;                   // 公司名称
    private Date dcaPlanAllocDate;             // 计划分配日期
    private Date dcaRealAllocDate;             // 实际分配日期
    private Date dcaPlanBlDate;                // 计划BL日期
    private Date dcaRealBlDate;                // 实际BL日期
    private String cpyWriteoffFlag;            // 公司核销标志
    private Integer cpyTotalODDays;            // 公司总逾期天数
    private String cpyTotalODAmt;          // 公司总逾期金额
    private String writeoffFlag;               // 核销标志
    private String dcareserveflg;              // 催收预留标志
    private Date dcareservedate;               // 催收预留日期
    private String cpyHaveHomeRecord;          // 公司有无户籍记录
    private Date cpyHomeRecoedDate;            // 公司户籍记录日期
    private String sendtodcaId;                // 发送催收ID
    private String contractRate;           // 合同利率
    private String reportStatus;               // 报告状态
    private String currSector;                 // 当前阶段
    private String currSubsector;              // 当前子阶段
    private String currStates;                // 当前状态
    private String dcaStatus;                  // 催收状态
    private String disValid;                   // 是否有效
    private String putsigleUser;               // 推送用户
    private String cpyCurrSubsector;           // 公司当前子阶段
    private String cpyCurrsector;              // 公司当前阶段
    private String dispatchStatus;             // 派单状态
    private String paymentMsgStatus;           // 付款消息状态
    private String cpyTotalDebtAmountAll;  // 公司总债务金额
    private String receiveStatus;              // 接收状态
    private String caseTaskId;                 // 案件任务ID
    private String relativeContractNos;        // 关联合同编号
    private Integer accountAge;                // 账龄
    private String isPayoff;                   // 是否已结清
    private String isPayoffDesc;               // 结清描述
    private String outsrcIsPayoff;             // 委外是否结清
    private Date balanceDate;                  // 结算日期
    private String financialProduct;           // 金融产品
    private String isAppoint;                  // 是否指定
    private String freezeFlag;                 // 冻结标志
    private String provinceCodeHj;             // 户籍省份代码
    private String provinceNameHj;             // 户籍省份名称
    private String leftMoney;              // 剩余金额

    // 以下为描述字段（Str结尾）
    private String accountTypeStr;             // 账户类型描述
    private String loseContactFlagStr;         // 失联标志描述
    private String dcaTypeStr;                 // 催收类型描述
    private String customerStatusStr;          // 客户状态描述
    private String carStatusStr;               // 车辆状态描述
    private String cpyWriteoffFlagStr;         // 公司核销标志描述
    private String writeoffFlagStr;            // 核销标志描述
    private String dcareserveflgStr;           // 催收预留标志描述
    private String cpyHaveHomeRecordStr;       // 公司有无户籍记录描述
    private String reportStatusStr;            // 报告状态描述
    private String currSectorDesc;             // 当前阶段描述
    private String currStatesDesc;             // 当前状态描述
    private String dcaStatusDesc;              // 催收状态描述
    private String dispatchStatusDesc;         // 派单状态描述
    private String isAppointDesc;              // 是否指定描述

    private String userId;
}