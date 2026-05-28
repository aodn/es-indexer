package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ContactsModel {
    protected String identifier;
    protected List<String> roles;
    protected String organization;
    protected String name;
    protected String position;

    protected List<String> emails;
    protected List<ContactsAddressModel> addresses;
    protected List<ContactsPhoneModel> phones;
    protected List<LinkModel> links;
}
