package au.org.aodn.esindexer.model;

// TODO: if more fields are needed to be filtered, please add more columns here
public record DataRecord(
        String time,
        double longitude,
        double latitude
) {}
