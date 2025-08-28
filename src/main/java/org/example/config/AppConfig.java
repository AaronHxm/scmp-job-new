package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AppConfig implements AsyncConfigurer {


    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    @Bean(name = "contractProcessorExecutor")
    public Executor contractProcessorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 根据14600KF处理器设置 (14核20线程)
//        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        int logical = Runtime.getRuntime().availableProcessors(); // 14600KF 一般是 20

        // ===== 如果主要是 I/O 型（HTTP 调用为主） =====
        int corePoolSize = Math.min(64, logical * 4); // 20*4=80，保守上限 64
        int maxPoolSize  = corePoolSize;              // 固定大小，避免抖动

        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("ContractProcessor-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "bigTaskExecutor")
    public Executor bigTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);   // 大任务并行数，可根据机器核数和任务量调
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("BigTask-");
        executor.initialize();
        return executor;
    }
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
    @Bean(name = "mainProcessorPool")
    public ThreadPoolTaskExecutor mainProcessorPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 14核20线程的优化配置
        executor.setCorePoolSize(16);      // 物理核心数+2
        executor.setMaxPoolSize(28);       // 接近线程数*1.5
        executor.setQueueCapacity(2000);    // 适中队列防止OOM
        executor.setThreadNamePrefix("Main-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }

    // 重试专用线程池（IO密集型）
    @Bean(name = "retryProcessorPool")
    public ThreadPoolTaskExecutor retryProcessorPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 重试任务通常有延迟，可以设置更大队列
        executor.setCorePoolSize(8);       // 约主池1/2
        executor.setMaxPoolSize(16);       // 约主池1/2
        executor.setQueueCapacity(2000);     // 更大的队列缓冲
        executor.setThreadNamePrefix("Retry-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(120);
        executor.initialize();
        return executor;
    }

    @Bean(name = "timeoutMonitorPool")
    public ScheduledExecutorService timeoutMonitorPool () {
       return Executors.newScheduledThreadPool(
                4, // 独立线程数
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("TimeoutMonitor-" + counter.getAndIncrement());
                        t.setDaemon(true);
                        t.setPriority(Thread.MAX_PRIORITY);
                        return t;
                    }
                }
        );
    }

    @Override
    public Executor getAsyncExecutor() {
        return mainProcessorPool();
    }
}
