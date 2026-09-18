package com.example.tomatomall.service.serviceImpl;
import com.example.tomatomall.po.Stockpile;
import com.example.tomatomall.repository.StockpileRepository;
import com.example.tomatomall.service.RedisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class StockCacheTest {
    @Mock RedisService redis;
    @Mock StockpileRepository stocks;
    @InjectMocks StockCacheServiceImpl service;
    @Test void redisFailureFallsBackToAvailableDatabaseStock() {
        when(redis.get("stock:product:1")).thenThrow(new IllegalStateException("down"));
        Stockpile s=new Stockpile();s.setAmount(10);s.setFrozen(7);when(stocks.findByProductId(1)).thenReturn(s);
        doThrow(new IllegalStateException("down")).when(redis).set(eq("stock:product:1"),any(),eq(30L),eq(java.util.concurrent.TimeUnit.SECONDS));
        assertThat(service.getStock(1)).isEqualTo(3);
    }
    @Test void cacheMissRebuildHasBoundedTtlAndNeverUnfreezesStock() {
        Stockpile s=new Stockpile();s.setAmount(10);s.setFrozen(7);when(stocks.findByProductId(1)).thenReturn(s);
        assertThat(service.getStock(1)).isEqualTo(3);verify(redis).set("stock:product:1",3L,30,java.util.concurrent.TimeUnit.SECONDS);
        assertThat(s.getFrozen()).isEqualTo(7);
        assertThatThrownBy(()->service.decrStock(1,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.incrStock(1,1)).isInstanceOf(IllegalArgumentException.class);
    }
}
