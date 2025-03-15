package com.hsbc.interview.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hsbc.interview.common.TransException;
import com.hsbc.interview.config.CacheLockManager;
import com.hsbc.interview.dto.TransDataDto;
import com.hsbc.interview.dto.TransQryRequest;
import com.hsbc.interview.dto.TransQryRsp;
import com.hsbc.interview.entity.Transaction;
import com.hsbc.interview.enums.MerchantEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.util.ConcurrentReferenceHashMap;
import static org.mockito.Mockito.verify;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {

    private TransactionService transactionService;
    private Cache<String, Transaction> mainCache;
    private Cache<String, Set<String>> userIndexCache;
    private Cache<String, Set<String>> merchantIndexCache;
    private CacheLockManager lockManager;

    private static final String USER_ID = "user1";
    private static final String TRANSACTION_ID = "1";
    private static final String MERCHANT_ID = "1";

    @BeforeEach
    void setUp() {
        mainCache = Mockito.mock(Cache.class);
        userIndexCache = Mockito.mock(Cache.class);
        merchantIndexCache = Mockito.mock(Cache.class);
        lockManager = Mockito.mock(CacheLockManager.class);

        transactionService = new TransactionService(mainCache, userIndexCache, merchantIndexCache, lockManager);
    }

    @Test
    void deleteTransaction_EmptyTransactionId_ThrowsException() {
        TransQryRequest request = new TransQryRequest();
        request.setTransactionId("");

        assertThrows(TransException.class, () -> transactionService.deleteTransaction(request));
    }

    @Test
    void deleteTransaction_LockFailed_ThrowsException() {
        TransQryRequest request = new TransQryRequest();
        request.setTransactionId("123");
        request.setUserId("user1");

        Mockito.when(lockManager.tryLock("CACHE_LOCK_PREFIX123")).thenReturn(false);

        assertThrows(TransException.class, () -> transactionService.deleteTransaction(request));
    }

    @Test
    void deleteTransaction_TransactionDoesNotExist_ThrowsException() {
        TransQryRequest request = new TransQryRequest();
        request.setTransactionId("123");
        request.setUserId("user1");

        Mockito.when(lockManager.tryLock("CACHE_LOCK_PREFIX123")).thenReturn(true);
        Mockito.when(mainCache.getIfPresent("123")).thenReturn(null);
        Mockito.doNothing().when(lockManager).unlock("CACHE_LOCK_PREFIX123");

        assertThrows(TransException.class, () -> transactionService.deleteTransaction(request));
    }

    @Test
    void deleteTransaction_UserMismatch_ThrowsException() {
        TransQryRequest request = new TransQryRequest();
        request.setTransactionId("123");
        request.setUserId("user1");

        Transaction transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.getUserId()).thenReturn("user2");

        Mockito.when(lockManager.tryLock("CACHE_LOCK_PREFIX123")).thenReturn(true);
        Mockito.when(mainCache.getIfPresent("123")).thenReturn(transaction);
        Mockito.doNothing().when(lockManager).unlock("CACHE_LOCK_PREFIX123");

        assertThrows(TransException.class, () -> transactionService.deleteTransaction(request));
    }

    @Test
    void deleteTransaction_SuccessfulDeletion_RemovesTransactionAndUpdatesIndex() {
        TransQryRequest request = new TransQryRequest();
        request.setTransactionId("123");
        request.setUserId("user1");

        Transaction transaction = Mockito.mock(Transaction.class);
        Set<String> userIdSet = new HashSet<>();
        userIdSet.add("123");

        Mockito.when(lockManager.tryLock("CACHE_LOCK_PREFIX123")).thenReturn(true);
        Mockito.when(mainCache.getIfPresent("123")).thenReturn(transaction);
        Mockito.when(transaction.getUserId()).thenReturn("user1");
        Mockito.doNothing().when(mainCache).invalidate("123");
        Mockito.when(userIndexCache.getIfPresent("user1")).thenReturn(userIdSet);
        Mockito.doNothing().when(userIndexCache).invalidate("user1");
        Mockito.doNothing().when(lockManager).unlock("CACHE_LOCK_PREFIX123");

        transactionService.deleteTransaction(request);
    }

    @Test
    void searchTrans_AllConditionsEmpty_ReturnsAllTransactions() {
        TransQryRequest request = new TransQryRequest();
        request.setPage(1);
        request.setPageSize(10);

        Transaction transaction1 = new Transaction();
        transaction1.setTransactionId("1");
        transaction1.setUserId("user1");
        transaction1.setMerchantId("1");

        Transaction transaction2 = new Transaction();
        transaction2.setTransactionId("2");
        transaction2.setUserId("user2");
        transaction2.setMerchantId("2");

        ConcurrentMap<String, Transaction> cacheMap = new ConcurrentReferenceHashMap<>();
        cacheMap.put("1", transaction1);
        cacheMap.put("2", transaction2);

        Mockito.when(mainCache.asMap()).thenReturn(cacheMap);

        TransQryRsp response = transactionService.searchTrans(request);

        assertEquals(2, response.getTotal());
        assertEquals(2, response.getTransList().size());
    }

    @Test
    void searchTrans_TransactionIdNotEmpty_ReturnsSingleTransaction() {
        TransQryRequest request = new TransQryRequest();
        request.setTransactionId("1");

        Transaction transaction = new Transaction();
        transaction.setTransactionId("1");
        transaction.setUserId("user1");
        transaction.setMerchantId("1");

        Mockito.when(mainCache.getIfPresent("1")).thenReturn(transaction);

        TransQryRsp response = transactionService.searchTrans(request);

        assertEquals(1, response.getTotal());
        assertEquals(1, response.getTransList().size());
        assertEquals("1", response.getTransList().get(0).getTransactionId());
    }

    @Test
    void searchTrans_UserIdAndMerchantIdNotEmpty_ReturnsTransactionsByUserAndMerchant() {
        TransQryRequest request = new TransQryRequest();
        request.setUserId("user1");
        request.setMerchantId("1");
        request.setPage(1);
        request.setPageSize(10);

        Transaction transaction = new Transaction();
        transaction.setTransactionId("1");
        transaction.setUserId("user1");
        transaction.setMerchantId("1");

        Set<String> userIds = new HashSet<>(Arrays.asList("1"));
        Set<String> merchantIds = new HashSet<>(Arrays.asList("1"));

        Mockito.when(userIndexCache.getIfPresent("user1")).thenReturn(userIds);
        Mockito.when(merchantIndexCache.getIfPresent("1")).thenReturn(merchantIds);
        Mockito.when(mainCache.getIfPresent("1")).thenReturn(transaction);

        TransQryRsp response = transactionService.searchTrans(request);

        assertEquals(1, response.getTotal());
        assertEquals(1, response.getTransList().size());
        assertEquals("1", response.getTransList().get(0).getTransactionId());
    }

    @Test
    void searchTrans_UserIdNotEmpty_ReturnsTransactionsByUserId() {
        TransQryRequest request = new TransQryRequest();
        request.setUserId("user1");
        request.setPage(1);
        request.setPageSize(10);

        Transaction transaction = new Transaction();
        transaction.setTransactionId("1");
        transaction.setUserId("user1");
        transaction.setMerchantId("1");

        Set<String> userIds = new HashSet<>(Arrays.asList("1"));

        Mockito.when(userIndexCache.getIfPresent("user1")).thenReturn(userIds);
        Mockito.when(mainCache.getIfPresent("1")).thenReturn(transaction);

        TransQryRsp response = transactionService.searchTrans(request);

        assertEquals(1, response.getTotal());
        assertEquals(1, response.getTransList().size());
        assertEquals("1", response.getTransList().get(0).getTransactionId());
    }

    @Test
    void searchTrans_MerchantIdNotEmpty_ReturnsTransactionsByMerchantId() {
        TransQryRequest request = new TransQryRequest();
        request.setMerchantId("1");
        request.setPage(1);
        request.setPageSize(10);

        Transaction transaction = new Transaction();
        transaction.setTransactionId("1");
        transaction.setUserId("user1");
        transaction.setMerchantId("1");

        Set<String> merchantIds = new HashSet<>(Arrays.asList("1"));

        Mockito.when(merchantIndexCache.getIfPresent("1")).thenReturn(merchantIds);
        Mockito.when(mainCache.getIfPresent("1")).thenReturn(transaction);

        TransQryRsp response = transactionService.searchTrans(request);

        assertEquals(1, response.getTotal());
        assertEquals(1, response.getTransList().size());
        assertEquals("1", response.getTransList().get(0).getTransactionId());
    }

    @Test
    void searchTrans_WithValidUserId_ReturnsTransactionsByUserId() {
        // Arrange
        TransQryRequest request = createRequest(USER_ID, 1, 10);

        Transaction transaction = createTransaction(TRANSACTION_ID, USER_ID, MERCHANT_ID);
        Set<String> userIds = new HashSet<>(Arrays.asList("1"));

        Mockito.when(userIndexCache.getIfPresent(USER_ID)).thenReturn(userIds);
        Mockito.when(mainCache.getIfPresent("1")).thenReturn(transaction);

        // Act
        TransQryRsp response = transactionService.searchTrans(request);

        // Assert
        assertEquals(1, response.getTotal());
        assertEquals(1, response.getTransList().size());
        assertEquals(TRANSACTION_ID, response.getTransList().get(0).getTransactionId());
    }

    @Test
    void searchTrans_WithEmptyUserId_ReturnsEmptyResponse() {
        // Arrange
        TransQryRequest request = createRequest("", 1, 10);
        // 模拟 mainCache.asMap() 返回空的 ConcurrentMap
        Mockito.when(mainCache.asMap()).thenReturn(new ConcurrentReferenceHashMap<>());


        // Act
        TransQryRsp response = transactionService.searchTrans(request);

        // Assert
        assertEquals(0, response.getTotal());
        assertEquals(0, response.getTransList().size());
    }

    @Test
    void searchTrans_WithInvalidPageOrPageSize_ReturnsEmptyResponse() {
        // Arrange
        TransQryRequest request = createRequest(USER_ID, -1, 10); // Invalid page

        // Act
        TransQryRsp response = transactionService.searchTrans(request);

        // Assert
        assertEquals(0, response.getTotal());
        assertEquals(0, response.getTransList().size());
    }

    @Test
    void searchTrans_WithCacheMiss_ReturnsEmptyResponse() {
        // Arrange
        TransQryRequest request = createRequest(USER_ID, 1, 10);

        Mockito.when(userIndexCache.getIfPresent(USER_ID)).thenReturn(null); // Cache miss

        // Act
        TransQryRsp response = transactionService.searchTrans(request);

        // Assert
        assertEquals(0, response.getTotal());
        assertEquals(0, response.getTransList().size());
    }

    @Test
    void searchTrans_WithExceptionInCache_ReturnsEmptyResponse() {
        // Arrange
        TransQryRequest request = createRequest(USER_ID, 1, 10);

        // 模拟 userIndexCache.getIfPresent(USER_ID) 抛出异常
        Mockito.when(userIndexCache.getIfPresent(USER_ID)).thenThrow(new RuntimeException("Cache error"));

        // Act
        TransQryRsp response = transactionService.searchTrans(request);

        // Assert
        assertEquals(0, response.getTotal(), "Total should be 0 when cache throws exception");
        assertEquals(0, response.getTransList().size(), "TransList should be empty when cache throws exception");

        // 验证异常是否被正确处理
        verify(userIndexCache).getIfPresent(USER_ID);
    }

    private TransQryRequest createRequest(String userId, int page, int pageSize) {
        TransQryRequest request = new TransQryRequest();
        request.setUserId(userId);
        request.setPage(page);
        request.setPageSize(pageSize);
        return request;
    }

    private Transaction createTransaction(String transactionId, String userId, String merchantId) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setUserId(userId);
        transaction.setMerchantId(merchantId);
        return transaction;
    }
}