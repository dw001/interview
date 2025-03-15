package com.hsbc.interview.dto;

import lombok.Data;

import java.util.List;
/**
 * 交易查询返回参数
 * @Date: 2025-03-15
 **/
@Data
public class TransQryRsp {
    private Integer total = 0 ;         // 总数
    private Integer pageSize;         // 每页行数
    private Integer page;         // 当前页数
    private List<TransDataDto> transList;
}
