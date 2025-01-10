package au.org.aodn.esindexer.model;

import au.org.aodn.esindexer.service.DataAccessService;
import au.org.aodn.stac.model.StacItemModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class DatasetProvider {

    protected Logger log = LoggerFactory.getLogger(DatasetProvider.class);
    protected final String uuid;
    protected YearMonth currentYearMonth;
    protected final YearMonth endYearMonth;
    protected final DataAccessService dataAccessService;

    public DatasetProvider(String uuid, LocalDate startDate, LocalDate endDate, DataAccessService dataAccessService) {
        this.uuid = uuid;
        this.dataAccessService = dataAccessService;
        this.currentYearMonth = YearMonth.from(startDate);
        this.endYearMonth = YearMonth.from(endDate);
    }

    public Iterable<List<StacItemModel>> getIterator() {
        return () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !currentYearMonth.isAfter(endYearMonth);
            }

            @Override
            public List<StacItemModel> next() {
                // please keep it for a while since it benefits the performance optimisation
                StopWatch timer = new StopWatch();
                timer.start(String.format("Data querying for %s %s", currentYearMonth.getYear(), currentYearMonth.getMonth()));
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                List<StacItemModel> data = dataAccessService.getIndexingDatasetBy(
                        uuid,
                        LocalDate.of(currentYearMonth.getYear(), currentYearMonth.getMonthValue(), 1),
                        LocalDate.of(currentYearMonth.getYear(), currentYearMonth.getMonthValue(), currentYearMonth.lengthOfMonth())
                );
                currentYearMonth = currentYearMonth.plusMonths(1);
                if (data.isEmpty()) {
                    return null;
                }

                timer.stop();
                log.info(timer.prettyPrint());
                return data;
            }
        };
    }
}
