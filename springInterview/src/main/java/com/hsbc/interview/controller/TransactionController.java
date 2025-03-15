package com.hsbc.interview.controller;

import com.hsbc.interview.dto.BaseResponse;
import com.hsbc.interview.dto.TransQryRequest;
import com.hsbc.interview.dto.TransQryRsp;
import com.hsbc.interview.entity.Transaction;
import com.hsbc.interview.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * @author: wangwei
 * @date: 2025-03-13
 **/
@RestController
@RequestMapping("/transaction")
public class TransactionController {

    @Autowired
    private TransactionService transService;

    /**
     * 创建交易数据
     *
     * @param trans 交易详细数据
     * @return 返回结果
     */
    @PostMapping("/createTrans")
    public BaseResponse<Void> create(@Valid @RequestBody Transaction trans) {
        //当前项目无用户验证应从token取提交新增操作的用户id
        trans.setCreateUser(trans.getUserId());
        transService.addTransaction(trans);
        return BaseResponse.success(null);
    }
    /**
     * 根据入参查询交易信息
     *
     * @param req 查询交易条件
     * @return 返回结果，总行数，当前页，当前页条数据及交易数据
     */
    @PostMapping("/getTransData")
    public BaseResponse <TransQryRsp> getData(@RequestBody TransQryRequest req) {
        return BaseResponse.success(transService.searchTrans(req));
    }
    /**
     * 根据入参更新交易信息
     *
     * @param trans 要变更的交易数据
     * @return 返回结果
     */
    @PostMapping("/updateTrans")
    public BaseResponse<Void> update(@RequestBody Transaction trans){
        //当前项目无用户验证应从token取提交更新操作的用户id
        trans.setUpdateUser(trans.getUserId());
        transService.updateTransaction(trans);
        return BaseResponse.success(null);

    }
    /**
     * 根据入参删除交易信息
     *
     * @param req 要删除的交易数据
     * @return 返回结果
     */
    @PostMapping("/deleteTrans")
    public BaseResponse<Void> delete(@RequestBody TransQryRequest req){
        transService.deleteTransaction(req);
        return BaseResponse.success(null);

    }
}
