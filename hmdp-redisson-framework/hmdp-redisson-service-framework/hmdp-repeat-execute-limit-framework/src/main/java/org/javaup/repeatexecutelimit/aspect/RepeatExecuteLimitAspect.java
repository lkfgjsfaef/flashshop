package org.javaup.repeatexecutelimit.aspect;

import org.javaup.constant.LockInfoType;
import org.javaup.exception.HmdpFrameException;
import org.javaup.handle.RedissonDataHandle;
import org.javaup.locallock.LocalLockCache;
import org.javaup.lockinfo.LockInfoHandle;
import org.javaup.lockinfo.factory.LockInfoHandleFactory;
import org.javaup.repeatexecutelimit.annotion.RepeatExecuteLimit;
import org.javaup.servicelock.LockType;
import org.javaup.servicelock.ServiceLocker;
import org.javaup.servicelock.factory.ServiceLockFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.javaup.repeatexecutelimit.constant.RepeatExecuteLimitConstant.PREFIX_NAME;
import static org.javaup.repeatexecutelimit.constant.RepeatExecuteLimitConstant.SUCCESS_FLAG;

/**
 * 重复执行限制切面（防重复提交）
 *
 * 防护按顺序分三层：
 * 1. 先查 Redis 里的执行标记位，命中说明已执行过，直接拒绝 —— 最快路径，挡住绝大部分重复请求；
 * 2. 本地 ReentrantLock 尝试加锁，拿不到立即失败 —— 在本机并发层面先削一层；
 * 3. Redisson 分布式公平锁 —— 保证集群范围内同一时刻只有一个请求真正执行；
 * 4. 执行成功后按 durationTime 回写标记位，到期自动失效，不会永久锁死业务。
 *
 * 注意 finally 里保证本地锁一定释放，避免异常情况下死锁。
 */
@Slf4j
@Aspect
@Order(-11)
@AllArgsConstructor
public class RepeatExecuteLimitAspect {
    
    private final LocalLockCache localLockCache;
    
    private final LockInfoHandleFactory lockInfoHandleFactory;
    
    private final ServiceLockFactory serviceLockFactory;
    
    private final RedissonDataHandle redissonDataHandle;
    
    
    @Around("@annotation(repeatLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatExecuteLimit repeatLimit) throws Throwable {
        long durationTime = repeatLimit.durationTime();
        String message = repeatLimit.message();
        Object obj;
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.REPEAT_EXECUTE_LIMIT);
        String lockName = lockInfoHandle.getLockName(joinPoint,repeatLimit.name(), repeatLimit.keys());
        String repeatFlagName = PREFIX_NAME + lockName;
        String flagObject = redissonDataHandle.get(repeatFlagName);
        if (SUCCESS_FLAG.equals(flagObject)) {
            throw new HmdpFrameException(message);
        }
        ReentrantLock localLock = localLockCache.getLock(lockName,true);
        boolean localLockResult = localLock.tryLock();
        if (!localLockResult) {
            throw new HmdpFrameException(message);
        }
        try {
            ServiceLocker lock = serviceLockFactory.getLock(LockType.Fair);
            boolean result = lock.tryLock(lockName, TimeUnit.SECONDS, 0);
            if (result) {
                try{
                    flagObject = redissonDataHandle.get(repeatFlagName);
                    if (SUCCESS_FLAG.equals(flagObject)) {
                        throw new HmdpFrameException(message);
                    }
                    obj = joinPoint.proceed();
                    if (durationTime > 0) {
                        try {
                            redissonDataHandle.set(repeatFlagName,SUCCESS_FLAG,durationTime,TimeUnit.SECONDS);
                        }catch (Exception e) {
                            log.error("getBucket error",e);
                        }
                    }
                    return obj;
                } finally {
                    lock.unlock(lockName);
                }
            }else{
                throw new HmdpFrameException(message);
            }
        }finally {
            localLock.unlock();
        }
    }
}
