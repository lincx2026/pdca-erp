package com.pcba.erp.common.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Data
@Configuration
@ConfigurationProperties(prefix = "pcba.oss")
public class OssConfig {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
    private String bucket;

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public OSS ossClient() {
        if (blank(endpoint) || blank(accessKeyId) || blank(accessKeySecret) || blank(bucket)) {
            throw new IllegalStateException("请在 .env 配置 OSS_ENDPOINT、OSS_BUCKET、OSS_ACCESS_KEY_ID、OSS_ACCESS_KEY_SECRET");
        }
        if (!endpoint.startsWith("https://")) {
            throw new IllegalStateException("OSS_ENDPOINT 必须使用 HTTPS");
        }
        // SDK 3.x treats every non-null token, including an empty string, as STS.
        return new OSSClientBuilder().build(endpoint,
                new DefaultCredentialProvider(accessKeyId, accessKeySecret,
                        blank(securityToken) ? null : securityToken));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
