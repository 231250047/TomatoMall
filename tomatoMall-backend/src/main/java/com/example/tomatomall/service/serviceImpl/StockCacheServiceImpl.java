package com.example.tomatomall.service.serviceImpl;
import com.example.tomatomall.po.Stockpile;
import com.example.tomatomall.repository.StockpileRepository;
import com.example.tomatomall.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
/** Display-only cache. MySQL conditional updates decide whether an order can reserve stock. */
@Service
public class StockCacheServiceImpl implements StockCacheService {
    @Autowired private RedisService redis;
    @Autowired private StockpileRepository stocks;
    private String key(Integer id) { return "stock:product:"+id; }
    @Override public void initStock(Integer id,Integer ignored) { syncStock(id); }
    @Override public Long decrStock(Integer id,Integer quantity) { throw new IllegalArgumentException("普通订单库存只能通过数据库订单事务预占"); }
    @Override public Long incrStock(Integer id,Integer quantity) { throw new IllegalArgumentException("普通订单库存只能通过数据库订单事务释放"); }
    @Override public Long getStock(Integer id) {
        try { Object cached=redis.get(key(id));if(cached!=null) return Long.valueOf(cached.toString()); }
        catch(RuntimeException ignored) { /* Read-through fallback when Redis is unavailable. */ }
        Stockpile s=stocks.findByProductId(id);long available=s==null?0:s.getAmount()-s.getFrozen();
        try { redis.set(key(id),available,30,TimeUnit.SECONDS); } catch(RuntimeException ignored) { }
        return available;
    }
    @Override public void syncStock(Integer id) {
        Stockpile s=stocks.findByProductId(id);
        if(s==null) redis.delete(key(id));else redis.set(key(id),s.getAmount()-s.getFrozen(),30,TimeUnit.SECONDS);
    }
    @Override public void syncAllStock() { for(Stockpile s:stocks.findAll()) syncStock(s.getProduct().getId()); }
    @Override public void deleteStockCache(Integer id) { redis.delete(key(id)); }
}
