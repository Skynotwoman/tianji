package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;

@Aspect
@RequiredArgsConstructor
public class MyLockAspect implements Ordered {

    private final MyLockFactory lockFactory;

    @Around("@annotation(myLock)")
    public Object tryLock(ProceedingJoinPoint pjp, MyLock myLock) throws Throwable {
        RLock lock = lockFactory.getLock(myLock.lockType(), myLock.name());
        /*switch (myLock.lockType()){
            case RE_ENTRANT_LOCK:
                lock = redissonClient.getLock(myLock.name());
                break;
            case FAIR_LOCK:
                lock = redissonClient.getFairLock(myLock.name());
                break;
            case WRITE_LOCK:
                lock = redissonClient.getReadWriteLock(myLock.name()).writeLock();
                break;
            case READ_LOCK:
                lock = redissonClient.getReadWriteLock(myLock.name()).readLock();
                break;
            default:
                throw new BizIllegalException("错误的锁的类型！");
        }*/

        boolean isLock = myLock.lockStrategy().tryLock(lock, myLock);

        if (isLock){

            throw new BizIllegalException("请求太频繁！");

        }
        try {
            return pjp.proceed();
        }finally {
            lock.unlock();
        }


    }

    @Override
    public int getOrder() {
        return 0;
    }
}
