package com.trendspot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AliOssProperties：阿里云文件上传属性类
 * 读取application-dev.yml中的配置，Spring启动后会自动加载
 */
@Component
@ConfigurationProperties(prefix = "spring.alioss")
@Data
public class AliOssProperties {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

}
