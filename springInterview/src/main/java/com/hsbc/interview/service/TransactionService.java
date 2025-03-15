package com.hsbc.interview.service;

import cn.hutool.core.date.DateUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.hsbc.interview.common.TransException;
import com.hsbc.interview.dto.TransDataDto;
import com.hsbc.interview.dto.TransQryRequest;
import com.hsbc.interview.dto.TransQryRsp;
import com.hsbc.interview.entity.Transaction;
import com.hsbc.interview.config.CacheLockManager;
import com.hsbc.interview.enums.MerchantEnum;
import lombok.extern.log4j.Log4j;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.hsbc.interview.common.Constant.CACHE_LOCK_PREFIX;
import static com.hsbc.interview.common.Constant.HTTP_FAIL_CODE;

@Service
@Log4j2
public class TransactionService {

    // 主缓存：业务流水ID -> Transaction
    private final Cache<String, Transaction> mainCache;

    // 索引缓存：USER_id -> 业务流水ID列表
    private final Cache<String, Set<String>> userIndexCache;

    // 索引缓存：商户ID -> 业务流水ID列表
    private final Cache<String, Set<String>> merchantIndexCache;

    private final CacheLockManager lockManager;
    /**
     * 构造一个TransactionService实例
     * 该服务负责处理与交易相关的操作，通过缓存来优化性能
     *
     * @param mainCache 主缓存，存储交易信息，key为交易ID，value为交易数据
     * @param userIndexCache 按用户缓存，用于快速按用户进行查询，key为用户ID，value为交易ID集合
     * @param merchantIndexCache 按商户缓存，用于快速按商户进行查询，key为商户ID，value为交易ID集合
     * @param lockManager 缓存锁，用于在并发环境下安全地访问和修改缓存
     */
    @Autowired
    public TransactionService(Cache<String, Transaction> mainCache,
                              Cache<String, Set<String>> userIndexCache,
                              Cache<String, Set<String>> merchantIndexCache,
                              CacheLockManager lockManager) {
        this.mainCache = mainCache;
        this.userIndexCache = userIndexCache;
        this.merchantIndexCache = merchantIndexCache;
        this.lockManager = lockManager;
    }
    /**
     * @methodName addTransaction
     * @description 接收转入的交易信息入库
     * @param transaction 交易数据
     * @return 无
     * @author wangwei
     * @date 2025/3/15
     */
    public void addTransaction(Transaction transaction) {
        String transactionId = this.generateTransactionId();
        String lockKey = CACHE_LOCK_PREFIX + transactionId;
        if (lockManager.tryLock(lockKey)) {
            try {
                //校验新流水是否重复
                if (mainCache.getIfPresent(transactionId) != null) {
                    log.error("交易流水【{}】已经存在请重试",transactionId);
                    throw new TransException(HTTP_FAIL_CODE,"交易流水已经存在请重试");
                }

                // 存入主缓存
                transaction.setTransDate(DateUtil.formatDateTime(new Date()));
                mainCache.put(transactionId, transaction);

                // 更新用户索引
                userIndexCache.asMap().compute(transaction.getUserId(), (k, v) -> {
                    Set<String> ids = (v == null) ? ConcurrentHashMap.newKeySet() : v;
                    ids.add(transactionId);
                    return ids;
                });

                // 更新商户索引
                merchantIndexCache.asMap().compute(transaction.getMerchantId(), (k, v) -> {
                    Set<String> ids = (v == null) ? ConcurrentHashMap.newKeySet() : v;
                    ids.add(transactionId);
                    return ids;
                });
            } finally {
                lockManager.unlock(lockKey);
            }
        } else {
            log.error("无法获取锁 transaction ID: " + transaction.getTransactionId());
            throw new RuntimeException("无法获取锁 transaction ID: " + transaction.getTransactionId());
        }
    }
    /**
     * @methodName getByUserId
     * @description 按用户信息分页查询交易数据
     * @param userId 用户ID
     * @param page 页码
     * @param pageSize 页大小
     * @param rsp 返回结果
     * @return
     * @author wangwei
     * @date 2025/3/15
     */
    public void getByUserId(String userId, int page, int pageSize,TransQryRsp rsp) {
        Set<String> ids = userIndexCache.getIfPresent(userId);
        if (ids == null || ids.isEmpty()) {
            return;
        }

        List<String> sortedIds = new ArrayList<>(ids);
        rsp.setTotal(ids.size());
        sortedIds.sort(String::compareTo);

        int offset = (page - 1) * pageSize;
        int limit = Math.min(offset + pageSize, sortedIds.size());
        if(offset>=sortedIds.size()){
            return;
        }

        List<TransDataDto> dtoList = sortedIds.subList(offset, limit).stream()
                .map(mainCache::getIfPresent)
                .filter(Objects::nonNull)
                .map(this::convertTrans).toList();
        rsp.setTransList(dtoList);
    }

    /**
     * @methodName getByMerchantId
     * @description 按商户信息分页查询交易数据
     * @param merchantId 商户ID
     * @param page 页码
     * @param pageSize 页大小
     * @param rsp 返回结果
     * @return
     * @author WANGWEI
     * @date 2025/3/15
     */
    public void getByMerchantId(String merchantId, int page, int pageSize,TransQryRsp rsp) {
        Set<String> ids = merchantIndexCache.getIfPresent(merchantId);
        if (ids == null || ids.isEmpty()) {
            return;
        }

        List<String> sortedIds = new ArrayList<>(ids);
        sortedIds.sort(String::compareTo);
        rsp.setTotal(sortedIds.size());
        int offset = (page - 1) * pageSize;
        int limit = Math.min(offset + pageSize, sortedIds.size());
        // 如果当前页起始行超出总数，直接返回空集合
        if(offset>=sortedIds.size()){
            return;
        }
        //返回交易数据集合
        List<TransDataDto> dtoList = sortedIds.subList(offset, limit).stream()
                .map(mainCache::getIfPresent)
                .filter(Objects::nonNull)
                .map(this::convertTrans).toList();
        rsp.setTransList(dtoList);
    }

    /**
     * @methodName getByUserAndMerchant
     * @description 按用户和商户信息分页查询交易数据
     * @param merchantId 商户ID
     * @param userId
     * @param page 页码
     * @param pageSize 页大小
     * @param rsp 返回结果
     * @return 无
     * @author wangwei
     * @date 2025/3/15
     */
    public void getByUserAndMerchant(String userId, String merchantId, int page, int pageSize,TransQryRsp rsp) {
        //分别按用户ID和商户ID查询
        Set<String> userIds = userIndexCache.getIfPresent(userId);
        Set<String> merchantIds = merchantIndexCache.getIfPresent(merchantId);

        if (userIds == null || merchantIds == null || userIds.isEmpty() || merchantIds.isEmpty()) {
            return;
        }

        // 取交集
        Set<String> intersection = new HashSet<>(userIds);
        intersection.retainAll(merchantIds);

        if (intersection.isEmpty()) {
            return;
        }

        List<String> ids = new ArrayList<>(intersection);
        rsp.setTotal(ids.size());
        ids.sort(String::compareTo);

        int offset = (page - 1) * pageSize;
        int limit = Math.min(offset + pageSize, ids.size());
        // 如果当前页起始行超出总数，直接返回空集合
        if(offset>=ids.size()){
            return;
        }
        List<TransDataDto> dtoList =  ids.subList(offset, limit).stream()
                .map(mainCache::getIfPresent)
                .filter(Objects::nonNull)
                .map(this::convertTrans).toList();
        rsp.setTransList(dtoList);
    }
    /**
     * @methodName deleteTransaction
     * @description 删除交易数据
     * @param req 请求参数
     * @return 无
     * @author wangwei
     * @date 2025/3/15
     */
    public void deleteTransaction(TransQryRequest req) {
        if(Strings.isBlank(req.getTransactionId())){
            log.error("交易流水【{}】不能为空",req.getTransactionId());
            throw new TransException(HTTP_FAIL_CODE,"交易流水不能为空");
        }
        String lockKey = CACHE_LOCK_PREFIX + req.getTransactionId();
        if (lockManager.tryLock(lockKey)) {
            try {
                Transaction oldTransaction = mainCache.getIfPresent(req.getTransactionId());

                if (oldTransaction == null) {
                    throw new TransException(HTTP_FAIL_CODE,"交易流水不存在");
                }

                if(!Objects.equals(oldTransaction.getUserId(),req.getUserId())){
                    throw new TransException(HTTP_FAIL_CODE,"交易流水【"+req.getTransactionId()+"】不属于当前用户交易,无权受理");
                }

                // 从主缓存中移除
                mainCache.invalidate(req.getTransactionId());

                // 从USER_id索引中移除
                this.onMainCacheEvict(req.getTransactionId(), oldTransaction);
            } finally {
                lockManager.unlock(lockKey);
            }
        } else {
            throw new TransException(HTTP_FAIL_CODE, "当前流水:" + req.getTransactionId()+ "正在被其他用户操作");
        }
    }
    /**
     * @methodName onMainCacheEvict
     * @description 删除数据时关联清理索引数据
     * @param key 交易流水
     * @param value 交易数据
     * @return
     * @author 18451
     * @date 2025/3/15
     */
    private void onMainCacheEvict(String key, Transaction value) {
        if (value != null) {
            // 从USER_id索引中移除
            userIndexCache.asMap().computeIfPresent(value.getUserId(), (k, v) -> {
                v.remove(key);
                return v.isEmpty() ? null : v; // 若空则删除索引键
            });

            // 从商户ID索引中移除
            merchantIndexCache.asMap().computeIfPresent(value.getMerchantId(), (k, v) -> {
                v.remove(key);
                return v.isEmpty() ? null : v;
            });
        }
    }
    /**
     * @methodName updateTransaction
     * @description 更新交易数据
     * @param transaction 交易数据
     * @return
     * @author 18451
     * @date 2025/3/15
     */
    // 修改交易数据
    public void updateTransaction(Transaction transaction) {
        if(Strings.isBlank(transaction.getTransactionId())){
            log.error("交易流水为空");
            throw new TransException(HTTP_FAIL_CODE,"交易流水不能为空");
        }
        String transactionId = transaction.getTransactionId();
        String lockKey =CACHE_LOCK_PREFIX + transactionId;

        if (lockManager.tryLock(lockKey)) {
            try {
                Transaction oldTransaction = mainCache.getIfPresent(transactionId);
                if (Objects.isNull(oldTransaction)) {
                    log.error("交易流水【{}】不存在", transactionId);
                    throw new TransException(HTTP_FAIL_CODE, "交易流水不存在");
                }
                if(!Objects.equals(oldTransaction.getUserId(),transaction.getUserId())){
                    log.error("当前交易【{}】的新用户【{}】有变动，不允许修改", transactionId,transaction.getUserId());
                    throw new TransException(HTTP_FAIL_CODE,"不允许修改当前交易【"+transaction.getTransactionId()+"】归属的用户");
                }

                transaction.setTransDate(oldTransaction.getTransDate());
                transaction.setCreateUser(oldTransaction.getCreateUser());
                transaction.setUpdDate(DateUtil.formatDateTime(new Date()));

                // 更新主缓存
                mainCache.put(transactionId, transaction);

                // 检查并更新用户索引，用户有变更则更新
                if (!Objects.equals(oldTransaction.getUserId(), transaction.getUserId())) {
                    // 从旧的用户索引中移除当前交易
                    userIndexCache.asMap().computeIfPresent(oldTransaction.getUserId(), (k, v) -> {
                        v.remove(transactionId);
                        return v.isEmpty() ? null : v;
                    });

                    // 更新新的用户索引
                    userIndexCache.asMap().compute(transaction.getUserId(), (k, v) -> {
                        Set<String> ids = (v == null) ? ConcurrentHashMap.newKeySet() : v;
                        ids.add(transactionId);
                        return ids;
                    });
                }

                // 检查并更新商户索引，商户
                if (!Objects.equals(oldTransaction.getMerchantId(), transaction.getMerchantId())) {
                    // 从旧的商户索引中移除
                    merchantIndexCache.asMap().computeIfPresent(oldTransaction.getMerchantId(), (k, v) -> {
                        v.remove(transactionId);
                        return v.isEmpty() ? null : v;
                    });

                    // 更新新的商户索引
                    merchantIndexCache.asMap().compute(transaction.getMerchantId(), (k, v) -> {
                        Set<String> ids = (v == null) ? ConcurrentHashMap.newKeySet() : v;
                        ids.add(transactionId);
                        return ids;
                    });
                }
            } finally {
                //完成操作释放锁
                lockManager.unlock(lockKey);
            }
        } else {
            throw new TransException(HTTP_FAIL_CODE, "当前流水:" + transactionId+ "正在被其他用户操作");
        }
    }
    /**
     * @methodName getAllData
     * @description 获取全部数据
     * @param page 页码
     * @param pageSize 每页大小
     * @param rsp 返回结果
     * @return
     * @author wangwei
     * @date 2025/3/15
     */
    private void getAllData(int page, int pageSize,TransQryRsp rsp){
        Set<String> allKeys = mainCache.asMap().keySet();
        if (allKeys == null || allKeys.isEmpty()) {
            rsp.setTotal(0);
            rsp.setTransList(Collections.emptyList());
            return;
        }

        List<String> ids = new ArrayList<>(allKeys);
        ids.sort(String::compareTo);

        int offset = (page - 1) * pageSize;
        int limit = Math.min(offset + pageSize, ids.size());
        // 如果当前页起始行超出总数，直接返回空集合
        if(offset>=ids.size()){
            return;
        }
        List<TransDataDto> dtoList = ids.subList(offset, limit).stream()
                .map(mainCache::getIfPresent)
                .filter(Objects::nonNull)
                .map(this::convertTrans)
                .toList();

        rsp.setTotal(allKeys.size());
        rsp.setTransList(dtoList);
    }
    /**
     * @methodName searchTrans
     * @description 根据条件查询交易数据，支持按用户ID、商户ID、交易流水号查询
     * 有流水号则只按单个流水号查询
     * 用户ID和商户ID同时存在则按用户ID+商户ID进行关联查询
     * @param req 查询条件
     * @return com.hsbc.interview.dto.TransQryRsp 查询返回DTO
     * @author wangwei
     * @date 2025/3/15
     */
    public TransQryRsp searchTrans(TransQryRequest req){
        TransQryRsp rsp=new TransQryRsp();
        rsp.setPage(req.getPage());
        rsp.setPageSize(req.getPageSize());
        List<TransDataDto> dtoList= new ArrayList<>();
        rsp.setTransList(dtoList);
        if(Strings.isBlank(req.getTransactionId()) && Strings.isBlank(req.getUserId()) && Strings.isBlank(req.getMerchantId())){
            getAllData(req.getPage(),req.getPageSize(),rsp);
            return rsp;
        }
        //有交易流水号优先用流水号查询
        if(Strings.isNotBlank(req.getTransactionId())){
            Transaction trans=mainCache.getIfPresent(req.getTransactionId());
            if(trans!=null){
                dtoList.add(convertTrans(trans) );
            }
            rsp.setTotal(1);
            return rsp;
        }
        //USER_ID和商户ID同时非空，则使用用户ID和商户ID联合查询
        if(Strings.isNotBlank(req.getUserId()) && Strings.isNotBlank(req.getMerchantId())){
            getByUserAndMerchant(req.getUserId(),req.getMerchantId(),req.getPage(),req.getPageSize(),rsp);
            return rsp;
        }
        //用户ID厚厚空则用用户ID查询
        if(Strings.isNotBlank(req.getUserId())){
            getByUserId(req.getUserId(),req.getPage(),req.getPageSize(),rsp);
            return rsp;
        }
        //商户ID厚厚空则用商户ID查询
        if(Strings.isNotBlank(req.getMerchantId())){
            getByMerchantId(req.getMerchantId(),req.getPage(),req.getPageSize(),rsp);
            return rsp;
        }
        return rsp;
    }
    /**
     * @methodName convertTrans
     * @description 转换数据
     * @param transaction 交易数据
     * @return TrnsDataDto 返回的DTO
     * @author 18451
     * @date 2025/3/15
     */
    private TransDataDto convertTrans(Transaction transaction){
        TransDataDto dto = new TransDataDto();
        BeanUtils.copyProperties(transaction,dto);
        dto.setMerchantName(MerchantEnum.getDescByCode(transaction.getMerchantId()));
        return dto;
    }

    // 获取最大 key
    private Optional<String> getMaxKey() {
        return mainCache.asMap().keySet().stream()
                .max(String::compareTo);
    }
    //生成交易流水，规则 T+YYYYMMDD+####
    public String generateTransactionId() {
        String maxKey = getMaxKey().orElse("");
        String curDate = DateUtil.format(new Date(),"yyyyMMdd");
        if(Strings.isBlank(maxKey)){
            return "T" +curDate+ String.format("%08d", 1);
        }
        String maxDate = maxKey.substring(1,9);

        if(!maxDate.equals(curDate)){
            return "T" +curDate+ String.format("%08d", 1);
        }else{
            int maxId = Integer.parseInt(maxKey.substring(9));
            return "T" +curDate+ String.format("%08d", maxId+1);
        }
    }
}
