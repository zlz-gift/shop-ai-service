package com.zlz.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.zlz.agent.mapper")
public class ShopAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopAiApplication.class, args);
    }
}
