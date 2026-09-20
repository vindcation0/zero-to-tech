package com.zerototech.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 将 SPA 客户端路由转发到 index.html，避免浏览器直接访问 /text-lab 或刷新时 404
        registry.addViewController("/text-lab").setViewName("forward:/index.html");
    }
}
