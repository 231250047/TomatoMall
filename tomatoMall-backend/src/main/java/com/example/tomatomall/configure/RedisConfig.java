package com.example.tomatomall.configure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 
 * 【Redis 核心理解】
 * 1. RedisTemplate 是 Spring 操作 Redis 的核心模板类
 * 2. 序列化配置决定了数据在 Redis 中的存储格式
 * 3. 使用 Jackson 序列化可以存储复杂对象，同时保持可读性
 * 
 * 【面试要点】
 * - 为什么要自定义序列化？默认的 JDK 序列化可读性差，占用空间大
 * - StringRedisSerializer：用于 key 的序列化，保证 key 可读
 * - Jackson2JsonRedisSerializer：用于 value 的序列化，支持复杂对象
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate
     * 
     * 【设计要点】
     * 1. Key 使用 String 序列化：保证在 Redis 客户端中可读
     * 2. Value 使用 JSON 序列化：支持复杂对象存储，同时保持可读性
     * 3. Hash 的 key/value 也使用相同策略
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 创建 Jackson 序列化器
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        
        // 配置 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        // 设置可见性，使得所有属性都可以序列化
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 启用类型信息，用于反序列化时恢复正确的类型
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        // 注册 Java 8 时间模块
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);

        // 使用 String 序列化器序列化 key
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        
        // Key 采用 String 序列化
        template.setKeySerializer(stringRedisSerializer);
        // Hash 的 Key 也采用 String 序列化
        template.setHashKeySerializer(stringRedisSerializer);
        // Value 采用 Jackson 序列化
        template.setValueSerializer(jackson2JsonRedisSerializer);
        // Hash 的 Value 也采用 Jackson 序列化
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        
        template.afterPropertiesSet();
        return template;
    }

    /**
     * StringRedisTemplate 专门用于操作 String 类型
     * 
     * 【使用场景】
     * - 计数器（PV/UV）
     * - 简单的 key-value 缓存
     * - 分布式锁
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
