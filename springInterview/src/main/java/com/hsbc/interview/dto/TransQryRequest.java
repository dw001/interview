package com.hsbc.interview.dto;

import lombok.Data;
/**
 * @author: wangwei
 * @description: 查询请求参数
 */
@Data
public class TransQryRequest {
    private String transactionId;         // 业务流水ID
    private String userId;     // 用户ID
    private String merchantId; // 商户ID
    private Integer pageSize = 10;
    private Integer page = 1;
}
