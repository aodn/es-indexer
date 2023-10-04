package au.org.aodn.esindexer.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ExtentModel {
    protected List<List<Double>> bbox;
}
