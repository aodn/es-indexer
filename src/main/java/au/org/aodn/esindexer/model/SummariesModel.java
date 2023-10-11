package au.org.aodn.esindexer.model;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
public class SummariesModel {
    protected Integer score;
    protected String status;
    protected List<ZonedDateTime> creation;
}
