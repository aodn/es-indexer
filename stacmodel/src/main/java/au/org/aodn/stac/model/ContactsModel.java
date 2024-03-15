package au.org.aodn.stac.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ContactsModel {
    protected List<String> emails;
    protected List<Map<String, Object>> addresses;
    protected String roles;
    protected String organization;
    protected String name;
    protected List<Map<String, String>> phones;
    protected List<Map<String, String>> links;
    protected String position;
}
