package au.org.aodn.cloudoptimized.model;

import au.org.aodn.cloudoptimized.model.geojson.FeatureCollectionGeoJson;
import au.org.aodn.cloudoptimized.service.DataAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;

public class DatasetProvider {

    protected Logger log = LoggerFactory.getLogger(DatasetProvider.class);
    protected final String uuid;
    protected YearMonth currentYearMonth;
    protected final YearMonth endYearMonth;
    protected final DataAccessService dataAccessService;
    protected final ConcurrentHashMap<YearMonth, FeatureCollectionGeoJson> featureCollectionCache = new ConcurrentHashMap<>();
    protected final List<MetadataFields> columns;
    private boolean isGettingData = false;


    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor();


    public DatasetProvider(
            String uuid,
            LocalDate startDate,
            LocalDate endDate,
            DataAccessService dataAccessService,
            List<MetadataFields> columns
    ) {
        this.uuid = uuid;
        this.dataAccessService = dataAccessService;
        this.currentYearMonth = YearMonth.from(startDate);
        this.endYearMonth = YearMonth.from(endDate);
        this.columns = columns;
        executorService.submit(this::queryAllData);
    }

    private record FeatureCollectionTask(YearMonth yearMonth, FeatureCollectionGeoJson featureCollection){}

    private void queryAllData() {

        isGettingData = true;
        try {
            while (!currentYearMonth.isAfter(endYearMonth)) {
                queryDataByMultiThreads();
            }
        } finally {
            executorService.shutdown();
            isGettingData = false;
        }
    }

    private void queryDataByMultiThreads() {

        //TODO: currently, multi-threading is only working well for local running data-access-service. May try to solve the problem in the future.
        final int THREADS_COUNT = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(THREADS_COUNT);
        List<Callable<FeatureCollectionTask>> tasks = new ArrayList<>();

        // declare tasks
        for (int i = 0; i < THREADS_COUNT; i++) {
            var taskYearMonth = YearMonth.from(currentYearMonth);
            tasks.add(() -> queryFeatureCollection(columns, taskYearMonth));
            currentYearMonth = currentYearMonth.plusMonths(1);
            if (currentYearMonth.isAfter(endYearMonth)) {
                break;
            }
        }

        // invoke all tasks and cache the results
        try {
            List<Future<FeatureCollectionTask>> futures = executorService.invokeAll(tasks);
            for (Future<FeatureCollectionTask> future : futures) {
                FeatureCollectionTask featureCollectionTask = future.get();
                if (featureCollectionTask != null && featureCollectionTask.featureCollection != null) {
                    featureCollectionCache.put(featureCollectionTask.yearMonth, featureCollectionTask.featureCollection);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error while fetching data", e);
        } finally {
            executorService.shutdown();
        }
    }

    public Iterable<FeatureCollectionGeoJson> getIterator() {

        return () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return currentYearMonth.isBefore(endYearMonth) || isGettingData;
            }

            @Override
            public FeatureCollectionGeoJson next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                while (getEarliestYearMonth() == null) {
                    try{
                        // TODO: use synchronized block , wait() and notify() to avoid busy waiting
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        log.error("Error while waiting for data", e);
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
                return featureCollectionCache.remove(getEarliestYearMonth());
            }
        };
    }

    private FeatureCollectionTask queryFeatureCollection(List<MetadataFields> columns, YearMonth yearMonth) {
        var featureCollection =  dataAccessService.getIndexingDatasetByMonth(
                uuid,
                yearMonth,
                columns
        );
        return new FeatureCollectionTask(yearMonth, featureCollection);
    }

    private YearMonth getEarliestYearMonth() {
        return featureCollectionCache.entrySet()
                .stream()
                .min(Map.Entry.comparingByKey())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
