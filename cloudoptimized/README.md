# About

This lib is expected to be share by other component, the main purpose is to abstract the layer of access to s3
cloud ready parquet / zarr.

# How it works

We use Spark and SparkQL to query data against S3 bucket, in theory we should setup a Spark cluster with multiple node
so that processing can happens in parallel. Without that we can only use a single machine with multiple thread.

For local run with 16 core, aka 16 tasks. A run to get the max TIME is around 4 mins. Most of the time spend on initial
scan on MAX timestamp as it need to go through all directory, once we have the max timestamp and site_code access
to individual partition is very fast.

We may be able to optimized this query by asking if max timestamp is > certain time if we know the site_code.

```text
2024-03-21T10:31:21.014+11:00  WARN 1422672 --- [           main] o.a.h.fs.s3a.impl.ConfigurationHelper    : Option fs.s3a.connection.establish.timeout is too low (5,000 ms). Setting to 15,000 ms instead
Standard Commons Logging discovery in action with spring-jcl: please remove commons-logging.jar from classpath in order to avoid potential conflicts
Standard Commons Logging discovery in action with spring-jcl: please remove commons-logging.jar from classpath in order to avoid potential conflicts
Standard Commons Logging discovery in action with spring-jcl: please remove commons-logging.jar from classpath in order to avoid potential conflicts
                                                                                == Parsed Logical Plan ==
'Sort ['timestamp DESC NULLS LAST], true
+- 'Aggregate ['site_code], ['site_code, 'max('timestamp) AS timestamp#6]
   +- 'UnresolvedRelation [DATASET], [], false

== Analyzed Logical Plan ==
site_code: string, timestamp: bigint
Sort [timestamp#6L DESC NULLS LAST], true
+- Aggregate [site_code#1], [site_code#1, max(timestamp#2L) AS timestamp#6L]
   +- SubqueryAlias dataset
      +- View (`DATASET`, [TIME#0L,site_code#1,timestamp#2L])
         +- Relation [TIME#0L,site_code#1,timestamp#2L] parquet

== Optimized Logical Plan ==
Sort [timestamp#6L DESC NULLS LAST], true
+- Aggregate [site_code#1], [site_code#1, max(timestamp#2L) AS timestamp#6L]
   +- Project [site_code#1, timestamp#2L]
      +- Relation [TIME#0L,site_code#1,timestamp#2L] parquet

== Physical Plan ==
AdaptiveSparkPlan isFinalPlan=false
+- Sort [timestamp#6L DESC NULLS LAST], true, 0
   +- Exchange rangepartitioning(timestamp#6L DESC NULLS LAST, 200), ENSURE_REQUIREMENTS, [plan_id=21]
      +- HashAggregate(keys=[site_code#1], functions=[max(timestamp#2L)], output=[site_code#1, timestamp#6L])
         +- Exchange hashpartitioning(site_code#1, 200), ENSURE_REQUIREMENTS, [plan_id=18]
            +- HashAggregate(keys=[site_code#1], functions=[partial_max(timestamp#2L)], output=[site_code#1, max#11L])
               +- FileScan parquet [site_code#1,timestamp#2L] Batched: true, DataFilters: [], Format: Parquet, Location: InMemoryFileIndex(1 paths)[s3a://imos-data-lab-optimised/parquet/raymond/anmn_ctd_ts_fv01], PartitionFilters: [], PushedFilters: [], ReadSchema: struct<>

                                                                                == Parsed Logical Plan ==
'Project ['max('TIME) AS time#19]
+- 'Filter (('timestamp = 1706745600) AND ('site_code = GBROTE))
   +- 'UnresolvedRelation [DATASET], [], false

== Analyzed Logical Plan ==
time: bigint
Aggregate [max(TIME#0L) AS time#19L]
+- Filter ((timestamp#2L = cast(1706745600 as bigint)) AND (site_code#1 = GBROTE))
   +- SubqueryAlias dataset
      +- View (`DATASET`, [TIME#0L,site_code#1,timestamp#2L])
         +- Relation [TIME#0L,site_code#1,timestamp#2L] parquet

== Optimized Logical Plan ==
Aggregate [max(TIME#0L) AS time#19L]
+- Project [TIME#0L]
   +- Filter ((isnotnull(timestamp#2L) AND isnotnull(site_code#1)) AND ((timestamp#2L = 1706745600) AND (site_code#1 = GBROTE)))
      +- Relation [TIME#0L,site_code#1,timestamp#2L] parquet

== Physical Plan ==
AdaptiveSparkPlan isFinalPlan=false
+- HashAggregate(keys=[], functions=[max(TIME#0L)], output=[time#19L])
   +- Exchange SinglePartition, ENSURE_REQUIREMENTS, [plan_id=136]
      +- HashAggregate(keys=[], functions=[partial_max(TIME#0L)], output=[max#23L])
         +- Project [TIME#0L]
            +- FileScan parquet [TIME#0L,site_code#1,timestamp#2L] Batched: true, DataFilters: [], Format: Parquet, Location: InMemoryFileIndex(1 paths)[s3a://imos-data-lab-optimised/parquet/raymond/anmn_ctd_ts_fv01], PartitionFilters: [isnotnull(timestamp#2L), isnotnull(site_code#1), (timestamp#2L = 1706745600), (site_code#1 = GBR..., PushedFilters: [], ReadSchema: struct<TIME:bigint>

2024-03-21T10:35:08.686+11:00  INFO 1422672 --- [           main] a.o.a.c.s.a.o.a.c.ParquetReader          : Max timestamp 1707091261000
2024-03-21T10:35:08.686+11:00  INFO 1422672 --- [           main] au.org.aodn.cloudoptimized.Main          : Max time 2024-02-05T11:01:01+11:00[Australia/Hobart]
2024-03-21T10:35:08.687+11:00  INFO 1422672 --- [           main] au.org.aodn.cloudoptimized.Main          : End testing
2024-03-21T10:49:36.459+11:00  WARN 1422672 --- [ionShutdownHook] .s.c.a.CommonAnnotationBeanPostProcessor : Destroy method on bean with name 'sparkConfig' threw an exception: org.springframework.beans.factory.BeanCreationNotAllowedException: Error creating bean with name 'createSparkSession': Singleton bean creation not allowed while singletons of this factory are in destruction (Do not request a bean from a BeanFactory in a destroy method implementation!)

Process finished with exit code 130 (interrupted by signal 2:SIGINT)
```

Now if we subset given a known site_code and timestamp, then it is pretty fast, 28 seconds

```text
2024-03-21T15:07:29.802+11:00  WARN 1565280 --- [           main] o.a.h.fs.s3a.impl.ConfigurationHelper    : Option fs.s3a.connection.establish.timeout is too low (5,000 ms). Setting to 15,000 ms instead
Standard Commons Logging discovery in action with spring-jcl: please remove commons-logging.jar from classpath in order to avoid potential conflicts
Standard Commons Logging discovery in action with spring-jcl: please remove commons-logging.jar from classpath in order to avoid potential conflicts
Standard Commons Logging discovery in action with spring-jcl: please remove commons-logging.jar from classpath in order to avoid potential conflicts
                                                                                == Parsed Logical Plan ==
'Project ['LATITUDE, 'LONGITUDE, 'CNDC, 'CNDC_quality_control, 'TEMP, 'TEMP_quality_control]
+- 'Filter (((('timestamp >= 1684645649) AND ('timestamp <= 1710994049)) AND ('site_code = GBROTE)) AND (('TIME >= 1684645649116000000) AND ('TIME <= 1710994049116000000)))
   +- 'UnresolvedRelation [DATASET], [], false

== Analyzed Logical Plan ==
LATITUDE: double, LONGITUDE: double, CNDC: float, CNDC_quality_control: float, TEMP: float, TEMP_quality_control: float
Project [LATITUDE#1, LONGITUDE#2, CNDC#4, CNDC_quality_control#5, TEMP#6, TEMP_quality_control#7]
+- Filter ((((timestamp#19 >= 1684645649) AND (timestamp#19 <= 1710994049)) AND (site_code#18 = GBROTE)) AND ((TIME#17L >= 1684645649116000000) AND (TIME#17L <= 1710994049116000000)))
   +- SubqueryAlias dataset
      +- View (`DATASET`, [TIMESERIES#0,LATITUDE#1,LONGITUDE#2,NOMINAL_DEPTH#3,CNDC#4,CNDC_quality_control#5,TEMP#6,TEMP_quality_control#7,PSAL#8,PSAL_quality_control#9,DEPTH#10,DEPTH_quality_control#11,DENS#12,DENS_quality_control#13,PRES_REL#14,PRES_REL_quality_control#15,filename#16,TIME#17L,site_code#18,timestamp#19])
         +- Relation [TIMESERIES#0,LATITUDE#1,LONGITUDE#2,NOMINAL_DEPTH#3,CNDC#4,CNDC_quality_control#5,TEMP#6,TEMP_quality_control#7,PSAL#8,PSAL_quality_control#9,DEPTH#10,DEPTH_quality_control#11,DENS#12,DENS_quality_control#13,PRES_REL#14,PRES_REL_quality_control#15,filename#16,TIME#17L,site_code#18,timestamp#19] parquet

== Optimized Logical Plan ==
Project [LATITUDE#1, LONGITUDE#2, CNDC#4, CNDC_quality_control#5, TEMP#6, TEMP_quality_control#7]
+- Filter (((isnotnull(timestamp#19) AND isnotnull(site_code#18)) AND isnotnull(TIME#17L)) AND ((((timestamp#19 >= 1684645649) AND (timestamp#19 <= 1710994049)) AND (site_code#18 = GBROTE)) AND ((TIME#17L >= 1684645649116000000) AND (TIME#17L <= 1710994049116000000))))
   +- Relation [TIMESERIES#0,LATITUDE#1,LONGITUDE#2,NOMINAL_DEPTH#3,CNDC#4,CNDC_quality_control#5,TEMP#6,TEMP_quality_control#7,PSAL#8,PSAL_quality_control#9,DEPTH#10,DEPTH_quality_control#11,DENS#12,DENS_quality_control#13,PRES_REL#14,PRES_REL_quality_control#15,filename#16,TIME#17L,site_code#18,timestamp#19] parquet

== Physical Plan ==
*(1) Project [LATITUDE#1, LONGITUDE#2, CNDC#4, CNDC_quality_control#5, TEMP#6, TEMP_quality_control#7]
+- *(1) Filter ((isnotnull(TIME#17L) AND (TIME#17L >= 1684645649116000000)) AND (TIME#17L <= 1710994049116000000))
   +- *(1) ColumnarToRow
      +- FileScan parquet [LATITUDE#1,LONGITUDE#2,CNDC#4,CNDC_quality_control#5,TEMP#6,TEMP_quality_control#7,TIME#17L,site_code#18,timestamp#19] Batched: true, DataFilters: [isnotnull(TIME#17L), (TIME#17L >= 1684645649116000000), (TIME#17L <= 1710994049116000000)], Format: Parquet, Location: InMemoryFileIndex(1 paths)[s3a://imos-data-lab-optimised/parquet/raymond/anmn_ctd_ts_fv01], PartitionFilters: [isnotnull(timestamp#19), isnotnull(site_code#18), (timestamp#19 >= 1684645649), (timestamp#19 <=..., PushedFilters: [IsNotNull(TIME), GreaterThanOrEqual(TIME,1684645649116000000), LessThanOrEqual(TIME,171099404911..., ReadSchema: struct<LATITUDE:double,LONGITUDE:double,CNDC:float,CNDC_quality_control:float,TEMP:float,TEMP_qua...

+---------+--------------+--------+--------------------+-------+--------------------+
| LATITUDE|     LONGITUDE|    CNDC|CNDC_quality_control|   TEMP|TEMP_quality_control|
+---------+--------------+--------+--------------------+-------+--------------------+
|-23.48385|152.1728333333|5.051423|                 0.0|22.0369|                 1.0|
|-23.48385|152.1728333333|5.052178|                 0.0|22.0447|                 1.0|
|-23.48385|152.1728333333|5.046645|                 0.0|21.9838|                 1.0|
|-23.48385|152.1728333333|5.046943|                 0.0|21.9944|                 1.0|
|-23.48385|152.1728333333|5.031664|                 0.0|21.8256|                 1.0|
|-23.48385|152.1728333333|5.027517|                 0.0|21.7848|                 1.0|
|-23.48385|152.1728333333|5.035333|                 0.0| 21.872|                 1.0|
|-23.48385|152.1728333333|5.048649|                 0.0|22.0141|                 1.0|
|-23.48385|152.1728333333|5.052396|                 0.0| 22.052|                 1.0|
|-23.48385|152.1728333333|5.042573|                 0.0|21.9439|                 1.0|
+---------+--------------+--------+--------------------+-------+--------------------+
only showing top 10 rows

2024-03-21T15:07:57.323+11:00  INFO 1565280 --- [           main] au.org.aodn.cloudoptimized.Main          : End testing
```


# Local run

You need to do sso before you can access S3 folder

```sh
# A profile of your choice but that profile needs to have access to S3 folder you choose.

aws sso login --profile default
```

After you login successfully, you can run the Main to test it, but remember to add this to the JVM options

```text
--add-exports java.base/sun.nio.ch=ALL-UNNAMED

```
