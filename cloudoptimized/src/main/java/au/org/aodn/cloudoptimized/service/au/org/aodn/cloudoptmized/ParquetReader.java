package au.org.aodn.cloudoptimized.service.au.org.aodn.cloudoptmized;

import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.apache.spark.sql.functions.desc;

@Slf4j
public class ParquetReader {

    @Value("${cloudoptimized.s3path}")
    protected String s3path;

    @Value("${cloudoptimzed.defaultTimeZone:Australia/Hobart}")
    protected String timeZone;

    @Autowired
    protected SparkSession session;

    public ZonedDateTime getDatasetLastUpdate(String filename) {

        Dataset<Row> df = session.read().parquet(s3path + filename);
        df.createOrReplaceTempView("parquetFile");

        if(log.isDebugEnabled()) {
            df.show();
        }

        // Only return the top most value as sorted by desc
        df.limit(1);
        df.sort(desc("timestamp"));

        Dataset<Row> target = session.sql("SELECT timestamp FROM parquetFile");

        return target.first() != null ?
                ZonedDateTime.ofInstant(target.first().getInstant(0), ZoneId.of(timeZone)) :
                null;
    }
}
