package au.org.aodn.esindexer.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Slf4j
@Component
public class CLIRunner implements CommandLineRunner {
    public static final String BATCH = "batch";
    public static final String JOB_NAME = "jobName";

    protected final BatchJobRunner batchJobRunner;
    protected final ApplicationArguments args;

    @Autowired
    public CLIRunner(ApplicationArguments args, BatchJobRunner batchJobRunner) {
        this.batchJobRunner = batchJobRunner;
        this.args = args;
    }

    @Override
    public void run(String... rawArgs) throws Exception {

        if (args.containsOption(BATCH)) {
            if (!args.containsOption(JOB_NAME) || args.getOptionValues(JOB_NAME).size() != 1) {
                log.error("Argument must have --jobName and contains one value only");
                System.exit(1);
            }

            List<String> jobName = args.getOptionValues(JOB_NAME);

            try {
                batchJobRunner.run(jobName.get(0), null);
            }
            catch (Exception e) {
                log.error("Batch job failed with exception: {}", e.getMessage());
                System.exit(1);
            }
            log.info("Batch job completed successfully.");
            System.exit(0);
        }

        // If not batch, do nothing (web server runs)
        log.info("Start application in web mode");
    }
}
