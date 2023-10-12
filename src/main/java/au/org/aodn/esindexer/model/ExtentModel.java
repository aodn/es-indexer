package au.org.aodn.esindexer.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ExtentModel {
    protected List<List<BigDecimal>> bbox;
}
