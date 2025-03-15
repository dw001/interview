# HSBC interview
项目基于 JDK-21 搭建\
主要功能为业务流水的写入，修改，删除与查询
1. 公共缓存定义 CacheConfig
2. 同步锁定义 CacheLockManager
3. 交易服务 TradeService
2. 交易实体 Transaction

服务定义：
1. 添加交易记录：POST /transaction/createTrans
2. 更新交易记录：POST /transaction/updateTrans
3. 删除交易记录：POST /transaction/deleteTrans
4. 查询交易记录：POST /transaction/queryTrans


    支持按流水号，用户号，商户的多种查询方式

   
    
   
