package com.kma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** KMA 独立服务启动入口。 */
@EnableScheduling
@SpringBootApplication
public class KmaApplication {

    public static void main(String[] args) {
        SpringApplication.run(KmaApplication.class, args);
    }
}

