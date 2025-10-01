package au.org.aodn.esindexer;

import au.org.aodn.esindexer.batch.BatchJobRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration.class )
public class Application {
    @PostConstruct
    void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private static final String BATCH = "batch";
    public static void main(String[] args) {

        System.out.println("args length: " + args.length);
        for (String arg : args) {
            System.out.println(arg);
        }
        // if the first argument is not "batch", start the web application as a backend server
        if (args.length == 0 || !args[0].equals(BATCH)) {
            SpringApplication.run(Application.class, args);
            return;
        }


        // otherwise, run the batch job
        if (args.length != 1 ) {
            System.err.println("Argument count mismatch. Arg count: " + args.length);
            System.exit(1);
        }

        ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .run(args);
        BatchJobRunner runner = context.getBean(BatchJobRunner.class);

        String jobName = System.getenv("INDEXER_BATCH_JOB_NAME");
        if (jobName == null) {
            System.err.println("Environment variable INDEXER_BATCH_JOB_NAME is not set.");
            System.exit(1);
        }
        String jobParam = System.getenv("INDEXER_BATCH_JOB_PARAM");

            try {
                runner.run(jobName, jobParam);
            } catch (Exception e) {
                System.err.println("Batch job failed with exception: " + e.getMessage());
                System.exit(1);
            }
            System.out.println("Batch job completed successfully.");
            System.exit(0);


    }
}
