package au.org.aodn.esindexer.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProviderModel {
    protected String name;
    protected String description;
    protected List<String> roles;
    protected String url;
}
