package au.org.aodn.cloudoptimized.model;

import au.org.aodn.cloudoptimized.model.geojson.FeatureCollectionGeoJson;
import au.org.aodn.cloudoptimized.service.DataAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
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
        executorService.submit(this::initializeCache);
    }

    private record FeatureCollectionTask(YearMonth yearMonth, FeatureCollectionGeoJson featureCollection){};

    private void initializeCache() {
        try {
            while (!isCacheFull() && !currentYearMonth.isAfter(endYearMonth)) {
                fetchData50Threads();
                System.out.println();
            }
        } finally {
            executorService.shutdown();
        }
    }

    private void fetchData50Threads() {
        final int THREADS_COUNT = 15;
        isGettingData = true;
        ExecutorService executorService = Executors.newFixedThreadPool(THREADS_COUNT);
        List<Callable<FeatureCollectionTask>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS_COUNT; i++) {
            var taskYearMonth = YearMonth.from(currentYearMonth);
            tasks.add(() -> getFeatureCollection(columns, taskYearMonth));
            currentYearMonth = currentYearMonth.plusMonths(1);
            if (currentYearMonth.isAfter(endYearMonth)) {
                break;
            }
        }

        try {
            List<Future<FeatureCollectionTask>> futures = executorService.invokeAll(tasks);
            for (Future<FeatureCollectionTask> future : futures) {
                FeatureCollectionTask featureCollectionTask = future.get();
                if (featureCollectionTask != null && featureCollectionTask.featureCollection != null) {
                    featureCollectionCache.put(featureCollectionTask.yearMonth, featureCollectionTask.featureCollection);
                }
            }
            isGettingData = false;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error while fetching data", e);
        } finally {
            executorService.shutdown();
        }
    }


    private boolean isCacheFull() {
        long maxHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
        long usedHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        double usedPercentage = (double) usedHeap / maxHeap * 100;

        log.info("Used heap: {}% of max heap", usedPercentage);

        // to make sure the app is safe, set a lower percentage
        return usedPercentage >= 50;
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

    private FeatureCollectionTask getFeatureCollection(List<MetadataFields> columns, YearMonth yearMonth) {
        var startDate = yearMonth.atDay(1);
        var endDate = yearMonth.atEndOfMonth();
        var featureCollection =  dataAccessService.getIndexingDatasetBy(
                uuid,startDate,
                endDate,
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
