package com.tenacy.pixiescale.mediastorage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {
    private String type = "local";
    private String baseDir = "${user.home}/pixiescale/media/output";
    private int uploadPartSize = 5;
    private int maxUploadSize = 2000;
}