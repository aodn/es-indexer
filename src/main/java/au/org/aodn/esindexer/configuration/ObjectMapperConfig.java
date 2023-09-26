package au.org.aodn.esindexer.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObjectMapperConfig {

    public static ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Enable pretty printing for JSON output
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Ignore unknown properties during deserialization
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

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
