package org.example.scheduled;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.Queue;

/**
 * 严格的时间窗口限流器
 * 确保在任意1秒时间窗口内不超过maxRequests次请求
 */
@Slf4j
public   class StrictRateLimiter {
    private final int maxRequests;
    private final long timeWindowMillis;
    private final Queue<Long> requestTimestamps;

    public StrictRateLimiter(int maxRequests, long timeWindowMillis) {
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.requestTimestamps = new LinkedList<>();
    }

    public synchronized void acquire() throws InterruptedException {
        long currentTime = System.currentTimeMillis();

        // 清理过期的请求时间戳
        while (!requestTimestamps.isEmpty() &&
                currentTime - requestTimestamps.peek() > timeWindowMillis) {
            requestTimestamps.poll();
        }

        // 如果当前时间窗口内请求数已达上限，等待
        if (requestTimestamps.size() >= maxRequests) {
            long oldestTimestamp = requestTimestamps.peek();
            long waitTime = timeWindowMillis - (currentTime - oldestTimestamp);

            if (waitTime > 0) {
                log.debug("Strict rate limiting: waiting {}ms", waitTime);
                Thread.sleep(waitTime);

                // 等待后重新清理和检查
                currentTime = System.currentTimeMillis();
                while (!requestTimestamps.isEmpty() &&
                        currentTime - requestTimestamps.peek() > timeWindowMillis) {
                    requestTimestamps.poll();
                }
            }
        }

        // 添加当前请求时间戳
        requestTimestamps.offer(System.currentTimeMillis());
    }
}