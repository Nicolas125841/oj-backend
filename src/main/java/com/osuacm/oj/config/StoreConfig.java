package com.osuacm.oj.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.HashMap;
import java.util.Map;

import static io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider.OPTIONS;
import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;

@Configuration
@EnableR2dbcRepositories
public class StoreConfig extends AbstractR2dbcConfiguration {

    private static final Log log = LogFactory.getLog(StoreConfig.class);

    @Value("${db.auth.username}")
    private String dbUsername;

    @Value("${db.auth.password}")
    private String dbPassword;

    @Value("${db.auth.database}")
    private String db;

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        Map<String, String> options = new HashMap<>();

        options.put("lock_timeout", "10s");
        options.put("statement_timeout", "300s");

        ConnectionFactory connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
            .option(DRIVER, "postgresql")
            .option(HOST, "postgres")
            .option(PORT, 5432)
            .option(USER, dbUsername)
            .option(PASSWORD, dbPassword)
            .option(DATABASE, db)
            .option(OPTIONS, options)
            .build());

        log.info("Using: " + connectionFactory);

        return connectionFactory;
    }
}
