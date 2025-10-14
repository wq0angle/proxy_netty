
package com.netty.server.entry;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.internal.PlatformDependent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.BufferPoolMXBean;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class MemClearEntry {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    private static final long THRESHOLD = 512 * 1024 * 1024;

    public void init() {
        scheduler.scheduleWithFixedDelay(this::monitorAndClean,
                30, 30, TimeUnit.SECONDS);
    }
    public void monitorAndClean() {
        long usedMemory = getDirectMemoryUsage();

        if (usedMemory > THRESHOLD) {
            log.warn("堆外内存过高，开始清理，占用: {} KB", usedMemory);
            cleanupOffHeapMemory();
        }
    }

    private void cleanupOffHeapMemory() {
        System.gc();
        cleanupDirectBuffers();
    }

    private long getDirectMemoryUsage() {
        try {
            List<BufferPoolMXBean> pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
            for (BufferPoolMXBean pool : pools) {
                if ("direct".equals(pool.getName())) {
                    return pool.getMemoryUsed();
                }
            }
        } catch (Exception e) {
            log.error("Failed to get direct memory usage", e);
        }
        return -1;
    }

    private void cleanupDirectBuffers() {
        // 实现具体的清理逻辑
    }
}