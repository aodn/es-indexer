package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ContactsModel {
    protected List<String> roles;
    protected String organization;
    protected String name;
    protected String position;

    // LinkedHashSet to retain order and remove duplicate address, the order is good for debug
    protected LinkedHashSet<String> emails;
    protected LinkedHashSet<ContactsAddressModel> addresses;
    protected LinkedHashSet<ContactsPhoneModel> phones;
    protected LinkedHashSet<LinkModel> links;
}
