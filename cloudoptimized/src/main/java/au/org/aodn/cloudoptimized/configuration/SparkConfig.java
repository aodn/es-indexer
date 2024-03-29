package au.org.aodn.cloudoptimized.configuration;

import au.org.aodn.cloudoptimized.service.au.org.aodn.cloudoptmized.ParquetReader;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import javax.annotation.PreDestroy;

/**
 * We tried DuckDb, and at the moment there are some issue with accessing s3 bucket so I
 * use the spark ql for now.
 */
@Configuration
public class SparkConfig {

    @Lazy
    @Autowired
    protected SparkSession sparkSession;

    @PreDestroy
    public void shutdown() {
        if(sparkSession != null) {
            sparkSession.stop();
        }
    }

    @Bean
    public SparkSession createSparkSession(@Value("${cloudoptimized.sparkSessionName:DEFAULT_SESSION}") String sessionName) {

        // TODO: Any needed? https://hadoop.apache.org/docs/stable/hadoop-aws/tools/hadoop-aws/performance.html#Thread_and_connection_pool_settings.
        SparkConf config = new SparkConf();

        config.set("spark.ui.enabled", "false");
        config.set("spark.ui.showConsoleProgress", "true");
        config.set("spark.hadoop.parquet.enable.summary-metadata", "false");

        // This is for write, read do not matter
        config.set("spark.hadoop.fs.s3a.bucket.all.committer.magic.enabled", "true");

        // Use [default] profile with SSO
        config.set("spark.hadoop.fs.s3a.aws.credentials.provider", "software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider");
        // config.set("spark.hadoop.fs.s3a.access.key", credentials['AccessKeyId']);
        // config.set("spark.hadoop.fs.s3a.secret.key", credentials['SecretAccessKey']);
        // config.set("spark.hadoop.fs.s3a.session.token", credentials['SessionToken']);

        config.set("spark.sql.parquet.mergeSchema", "true");
        config.set("spark.sql.parquet.filterPushdown", "true");
        config.set("spark.sql.parquet.aggregatePushdown", "true");
        config.set("spark.sql.hive.metastorePartitionPruning", "true");

        // Need because panda parquet set time to nano-seconds, we need that to be long during parquet read.
        config.set("spark.sql.legacy.parquet.nanosAsLong", "true");
        config.set("spark.sql.sources.commitProtocolClass","org.apache.spark.internal.io.cloud.PathOutputCommitProtocol");
        config.set("spark.sql.parquet.output.committer.class", "org.apache.spark.internal.io.cloud.BindingParquetOutputCommitter");

        return SparkSession
                .builder()
                .appName(sessionName)
                .master("local[*]")
                .config(config)
                .getOrCreate();
    }

    @Bean
    public ParquetReader createParquetReader() {
        return new ParquetReader();
    }

}
