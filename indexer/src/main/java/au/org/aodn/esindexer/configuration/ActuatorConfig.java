package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.service.VocabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActuatorConfig {
    @Autowired
    protected VocabService service;

    @Bean
    public HealthIndicator indexerHealth() {
        return () ->
            service.health();
    }
}
