package au.org.aodn.esindexer.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

@Slf4j
@Component
public class CLIRunner implements CommandLineRunner {
    private static final String BATCH = "batch";
    private final BatchJobRunner batchJobRunner;
    private final ConfigurableApplicationContext context;

    @Autowired
    public CLIRunner(BatchJobRunner batchJobRunner, ConfigurableApplicationContext context) {
        this.batchJobRunner = batchJobRunner;
        this.context = context;
    }

    @Override
    public void run(String... args) throws Exception {

        if (args.length > 0 && BATCH.equals(args[0])) {
            if (args.length < 2 || args.length > 3) {
                log.error("Argument count mismatch. Arg count: " + args.length);
                System.exit(1);
            }
            String jobName = args[1];
            String jobParam = args.length == 3 ? args[2] : null;
            try {
                batchJobRunner.run(jobName, jobParam);
            } catch (Exception e) {
                log.error("Batch job failed with exception: " + e.getMessage());
                System.exit(1);
            }
            log.info("Batch job completed successfully.");
            context.close();
            System.exit(0);
        }

        // If not batch, do nothing (web server runs)
        log.info("Web Application started with arguments: {}", Arrays.toString(args));
    }
}
