package com.wos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档(Knife4j / OpenAPI 3)配置。
 * <p>
 * 启动后访问 /doc.html 查看接口文档。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI workOrderOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("智能工单系统 API")
                .description("智能工单管理系统接口文档")
                .version("v1.0"));
    }
}
