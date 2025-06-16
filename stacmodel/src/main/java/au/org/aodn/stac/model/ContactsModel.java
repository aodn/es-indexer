package au.org.aodn.stac.model;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.List;

@Data
@Builder
public class ContactsModel {
    protected List<String> roles;
    protected String organization;
    protected String name;
    protected String position;

    // LinkedHashSet to retain order and remove duplicate address, the order is good for debug
    @Builder.Default
    protected LinkedHashSet<String> emails = new LinkedHashSet<>();
    @Builder.Default
    protected LinkedHashSet<ContactsAddressModel> addresses = new LinkedHashSet<>();
    @Builder.Default
    protected LinkedHashSet<ContactsPhoneModel> phones = new LinkedHashSet<>();
    @Builder.Default
    protected LinkedHashSet<LinkModel> links = new LinkedHashSet<>();
}
