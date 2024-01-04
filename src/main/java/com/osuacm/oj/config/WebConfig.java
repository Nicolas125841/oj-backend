package com.osuacm.oj.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.reactive.config.*;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableWebFlux
public class WebConfig implements WebFluxConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/problemData/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/problemData/**")
                .addResourceLocations("file:/problems")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS));
    }
}
