package au.org.aodn.stac.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactsPhoneModel {
    protected List<String> roles;
    protected String value;

    @Override
    public int hashCode() {
        return Objects.hash(roles, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ContactsPhoneModel that = (ContactsPhoneModel)o;
        return Objects.deepEquals(roles, that.roles)
                && Objects.equals(value, that.value);
    }
}
