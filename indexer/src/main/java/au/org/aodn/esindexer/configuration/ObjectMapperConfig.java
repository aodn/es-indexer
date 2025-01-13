package au.org.aodn.esindexer.configuration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObjectMapperConfig {
    @Bean("indexerObjectMapper")
    public static ObjectMapper objectMapper() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                // Enable pretty printing for JSON output
                .enable(SerializationFeature.INDENT_OUTPUT)
                // Ignore unknown properties during deserialization
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        // Use a specific date format for serialization and deserialization (if needed)
        // objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));

        // Configure property naming strategy (e.g., to use snake_case)
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

        // Register custom serializers/deserializers if needed
        // SimpleModule customModule = new SimpleModule();
        // customModule.addSerializer(CustomClass.class, new CustomSerializer());
        // customModule.addDeserializer(CustomClass.class, new CustomDeserializer());
        // objectMapper.registerModule(customModule);

        // Enable Java 8 date/time support (if needed)
        // objectMapper.registerModule(new JavaTimeModule());

        return objectMapper;
    }
}
