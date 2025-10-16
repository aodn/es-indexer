package au.org.aodn.esindexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.TimeZone;

import static au.org.aodn.esindexer.batch.CLIRunner.BATCH;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration.class )
public class Application {
    @PostConstruct
    void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);

        boolean isBatchMode = Arrays.asList(args).contains("--" + BATCH);

        if(isBatchMode) {
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setAdditionalProfiles(BATCH);
        }
        app.run(args);
    }
}
