package com.example.supermarket.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 本地存储模式下的静态资源映射：将 /uploads/** 映射到本地磁盘目录，
 * 使上传的图片可通过 /api/uploads/... 直接访问。仅在 local 模式实际产生文件，
 * OSS 模式此映射无害（目录为空）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${storage.local.dir:./uploads}")
    private String baseDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path abs = Paths.get(baseDir).toAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + abs + "/")
                .setCachePeriod(3600);
    }
}
