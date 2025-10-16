
package com.netty.server.entry;

import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
        long usedDirectMemory = getDirectMemoryUsage();
        long usedHeapMemory = getHeapMemoryUsage();

        if (usedDirectMemory > THRESHOLD) {
            log.info("堆外内存过高，开始清理，占用: {} KB", usedDirectMemory / 1024);
            cleanupOffHeapMemory();
            log.info("清理完成");
            return;
        }

        if (usedHeapMemory > THRESHOLD) {
            log.info("堆内存过高，开始清理，占用: {} KB", usedHeapMemory);
            cleanupOffHeapMemory();
            log.info("清理完成");
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

    private long getHeapMemoryUsage() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        return memBean.getHeapMemoryUsage().getUsed();
    }

    private void cleanupDirectBuffers() {
        // 实现具体的清理逻辑
    }
}