package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.security.APIKeyAuthFilter;
import java.util.Objects;

import au.org.aodn.esindexer.security.JwtAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Order(1)
public class SecurityConfig {

    @Value("${app.http.auth-token-header-name}")
    private String principalRequestHeader;

    @Value("${app.http.authToken}")
    private String principalRequestValue;

    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        APIKeyAuthFilter filter = new APIKeyAuthFilter(principalRequestHeader);

        System.out.print("principalRequestValue: " + principalRequestValue);

        filter.setAuthenticationManager(
            authentication -> {
                String principal = (String) authentication.getPrincipal();
                if (!Objects.equals(principalRequestValue, principal)) {
                    throw new BadCredentialsException("Invalid API key.");
                }
                authentication.setAuthenticated(true);
                return authentication;
            });

        http.cors()
            .and()
            .csrf()
            .disable()
            .exceptionHandling()
            .authenticationEntryPoint(unauthorizedHandler)
            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .addFilter(filter)
            .authorizeRequests()
            .antMatchers("/error")
            .permitAll()
            .antMatchers("/",
                "/favicon.ico",
                "/**/*.png",
                "/**/*.gif",
                "/**/*.svg",
                "/**/*.jpg",
                "/**/*.html",
                "/**/*.css",
                "/**/*.js")
            .permitAll()
            .antMatchers(HttpMethod.GET, "/api/v1/indexer/index/gn_records/**")
            .permitAll()
            .antMatchers(HttpMethod.GET, "/api/v1/indexer/index/records/**")
            .permitAll()
            .antMatchers(
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/swagger-ui.html")
            .permitAll()
            .anyRequest()
            .authenticated();

        return http.build();
    }
}

