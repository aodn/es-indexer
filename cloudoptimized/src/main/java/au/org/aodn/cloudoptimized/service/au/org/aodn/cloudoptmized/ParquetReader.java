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

    protected StructType schema = new StructType(
            new StructField[] {
                    new StructField("site_code", DataTypes.StringType, false, Metadata.empty()),
                    new StructField("timestamp", DataTypes.LongType, false, Metadata.empty()),
                    new StructField("TIME", DataTypes.LongType, false, Metadata.empty()),
            }
    );

    public ZonedDateTime getDatasetMaxTimestamp(String filename) {

        Dataset<Row> df = session.read()
                .schema(schema)
                .parquet(s3path + filename);

        df.createOrReplaceTempView("DATASET");
        // TODO: Seems slow operation, may be use s3 ListObject faster?
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

    public void getDatasetFromRange(String filename, String siteCode, ZonedDateTime start, ZonedDateTime end) {
        Dataset<Row> df = session.read()
                .parquet(s3path + filename);

        df.createOrReplaceTempView("DATASET");

        // timestamp is in sec but TIME is in nanosecond
        String sql = String.format("select LATITUDE,LONGITUDE,CNDC,CNDC_quality_control,TEMP,TEMP_quality_control from DATASET where timestamp >= %d and timestamp <= %d and site_code='%s' and TIME >= %d and TIME <= %d",
                start.toInstant().getEpochSecond(),
                end.toInstant().getEpochSecond(),
                siteCode,
                TimeUnit.NANOSECONDS.convert(start.toInstant().toEpochMilli(), TimeUnit.MILLISECONDS),
                TimeUnit.NANOSECONDS.convert(end.toInstant().toEpochMilli(), TimeUnit.MILLISECONDS)
        );

        Dataset<Row> rows = session.sql(sql);
        rows.explain(true);

        rows.show(10);
    }
}
