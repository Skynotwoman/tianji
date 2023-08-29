package com.tianji.promotion.utils;

import com.tianji.common.utils.BooleanUtils;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class RedisLock {

    private final String key;

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(long leaseTime, TimeUnit unit){
        String value = Thread.currentThread().getName();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, leaseTime, unit);
        return BooleanUtils.isTrue(success);
    }

    public void unLock(){
        redisTemplate.delete(key);
    }
}
