package com.tenacy.pixiescale.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {
    private String type = "local"; // local, s3, minio 등
    private String baseDir = "media-files";
    private int uploadPartSize = 5; // MB
    private int maxUploadSize = 2000; // MB
}