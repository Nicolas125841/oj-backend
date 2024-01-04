package com.osuacm.oj.config;

import com.osuacm.oj.utils.FileUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class UtilsConfig {

    @Bean
    FileUtil fileUtil(){
        return new FileUtil();
    }
}
