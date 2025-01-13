package au.org.aodn.esindexer.model;

import java.util.List;

public record DatasetEsEntry(
        String uuid,
        String yearMonth,
        List<CloudOptimizedEntry> data) {

}
