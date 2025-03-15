package com.hsbc.interview.entity;


import lombok.Data;
import org.springframework.format.annotation.NumberFormat;


import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

import static org.springframework.format.annotation.NumberFormat.Style.CURRENCY;
/**
 * 交易实体类
 * @author wangwei
 * @date 2025-03-15
 **/
@Data
public class Transaction {
    private String transactionId;         // 业务流水ID
    @NotNull(message = "用户ID不可为空")
    private String userId;     // 用户ID
    @NotNull(message = "商户ID不可为空")
    private String merchantId; // 商户ID
    @NotNull(message = "用户ID不可为空")
    @Digits(integer = 10, fraction = 4, message = "金额最多允许四位小数")
    @NumberFormat(style = CURRENCY)
    private BigDecimal amount; // 金额
    private String createUser;//流水创建人ID
    private String transDate; //交易日期
    private String updDate;//更新时间
    private String updateUser;
}