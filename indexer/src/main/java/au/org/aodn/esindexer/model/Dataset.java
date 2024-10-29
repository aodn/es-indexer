package au.org.aodn.esindexer.model;

import java.time.YearMonth;
import java.util.List;

public record Dataset(
        String uuid,
        YearMonth yearMonth,
        List<Datum> data
) {}
