package au.org.aodn.esindexer.model;

import java.util.List;

public record Dataset(
        String uuid,
        List<DataRecord> data
) {}
