package com.osuacm.oj.config;

import com.osuacm.oj.services.ProblemService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebFluxSecurity
public class AdminSecurityConfig {

    private static final Log log = LogFactory.getLog(ProblemService.class);

    @Value("${admin.auth.username}")
    private String adminUsername;

    @Value("${admin.auth.password}")
    private String adminPassword;

    @Bean
    public MapReactiveUserDetailsService userDetailsService() {
        UserDetails user = User.builder()
            .username(adminUsername)
            .password("{noop}".concat(adminPassword))
            .roles("ADMIN")
            .build();

        return new MapReactiveUserDetailsService(user);
    }

    @Bean
    SecurityWebFilterChain springProblemFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/problems/**"))
            .authorizeExchange(authorize -> authorize
                .pathMatchers(HttpMethod.GET).permitAll()
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                .pathMatchers(HttpMethod.DELETE).authenticated()
                .pathMatchers(HttpMethod.POST).authenticated()
                .anyExchange().denyAll()
            )
            .httpBasic(withDefaults());

        return http.build();
    }

    @Bean
    SecurityWebFilterChain springWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/auth/test"))
            .authorizeExchange((authorize) -> authorize
                .anyExchange().authenticated()
            )
            .httpBasic(withDefaults());

        return http.build();
    }
}
