package au.org.aodn.esindexer.model;

import au.org.aodn.esindexer.service.DataAccessService;
import org.springframework.util.StopWatch;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class DatasetProvider {

    private final String uuid;
    private YearMonth currentYearMonth;
    private final YearMonth endYearMonth;
    private final DataAccessService dataAccessService;

    public DatasetProvider(String uuid, LocalDate startDate, LocalDate endDate, DataAccessService dataAccessService) {
        this.uuid = uuid;
        this.dataAccessService = dataAccessService;
        this.currentYearMonth = YearMonth.from(startDate);
        this.endYearMonth = YearMonth.from(endDate);
    }

    public Iterable<DatasetEsEntry> getIterator() {
        return () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !currentYearMonth.isAfter(endYearMonth);
            }

            @Override
            public DatasetEsEntry next() {
                // please keep it for a while since it benefits the performance optimisation
                StopWatch timer = new StopWatch();
                timer.start("Data querying");
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                List<Datum> data = Arrays.stream(dataAccessService.getIndexingDatasetBy(
                        uuid,
                        LocalDate.of(currentYearMonth.getYear(), currentYearMonth.getMonthValue(), 1),
                        LocalDate.of(currentYearMonth.getYear(), currentYearMonth.getMonthValue(), currentYearMonth.lengthOfMonth())
                )).toList();
                currentYearMonth = currentYearMonth.plusMonths(1);
                if (data.isEmpty()) {
                    return null;
                }

                timer.stop();
                System.out.println(timer.prettyPrint());
                return new DatasetEsEntry(uuid, currentYearMonth.toString(), data);
            }
        };
    }
}