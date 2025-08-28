package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 启用定时任务支持
public class ScheduleDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleDemoApplication.class, args);
        System.out.println("定时任务服务已启动！");

    }
}
