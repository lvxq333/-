package com.trendspot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.trendspot.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class TrendSpotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrendSpotApplication.class, args);
    }

}
