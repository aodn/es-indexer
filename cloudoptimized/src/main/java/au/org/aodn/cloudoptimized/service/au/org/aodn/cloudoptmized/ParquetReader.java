package au.org.aodn.cloudoptimized.service.au.org.aodn.cloudoptmized;

import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.spark.sql.functions.*;

@Slf4j
public class ParquetReader {

    @Value("${cloudoptimized.s3path}")
    protected String s3path;

    @Value("${cloudoptimzed.defaultTimeZone:Australia/Hobart}")
    protected String timeZone;

    @Autowired
    protected SparkSession session;

    public ZonedDateTime getDatasetLastUpdate(String filename) {

        StructType schema = new StructType(
                new StructField[] {
                        new StructField("site_code", DataTypes.StringType, false, Metadata.empty()),
                        new StructField("timestamp", DataTypes.LongType, false, Metadata.empty()),
                        new StructField("TIME", DataTypes.LongType, false, Metadata.empty()),
                }
        );

        Dataset<Row> df = session.read()
                .schema(schema)
                .parquet(s3path + filename);

        df.createOrReplaceTempView("DATASET");

        Dataset<Row> findMaxTimestampIndex = session.sql("select site_code, max(timestamp) as timestamp from DATASET group by site_code order by timestamp desc");
        findMaxTimestampIndex.explain(true);

        String sql = String.format("select max(TIME) as time from DATASET where timestamp=%s and site_code='%s'",
                findMaxTimestampIndex.first().getLong(1),
                findMaxTimestampIndex.first().getString(0));

        Dataset<Row> findMaxTimestamp = session.sql(sql);
        findMaxTimestamp.explain(true);

        // Mostly data generated using panada and use unix time which is in nanosecond
        long t = TimeUnit.MILLISECONDS.convert(findMaxTimestamp.first().getLong(0), TimeUnit.NANOSECONDS);
        log.info("Max timestamp {}", t);

        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.of(timeZone));

    }
}
