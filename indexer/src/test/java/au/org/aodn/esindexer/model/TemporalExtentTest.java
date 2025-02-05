package au.org.aodn.esindexer.model;

import au.org.aodn.cloudoptimized.model.TemporalExtent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TemporalExtentTest {

    @Test
    public void verifyTemporalExtent() {
        TemporalExtent temporalExtent = TemporalExtent.builder()
                .startDate("2020-09-01T00:00:00+0000")
                .endDate("2024-06-01T00:00:00+0000")
                .build();

        // No error on calling getLocalDate so parse correct
        LocalDate d = temporalExtent.getLocalStartDate();
        assertNotNull(d);

        d = temporalExtent.getLocalEndDate();
        assertNotNull(d);
    }
}
