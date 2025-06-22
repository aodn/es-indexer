package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ContactsAddressModel {
    protected List<String> deliveryPoint;
    protected String city;
    protected String country;
    protected String postalCode;
    protected String administrativeArea;

    @JsonIgnore
    public boolean isEmpty() {
        return (deliveryPoint == null || deliveryPoint.isEmpty() || deliveryPoint.stream().allMatch(String::isBlank))
                && (city == null || city.isEmpty() || city.isBlank())
                && (country == null || country.isEmpty() || country.isBlank())
                && (postalCode == null || postalCode.isEmpty() || postalCode.isBlank())
                && (administrativeArea == null || administrativeArea.isEmpty() || administrativeArea.isBlank());
    }

    @Override
    public int hashCode() {
        return Objects.hash(deliveryPoint, city, country, postalCode, administrativeArea);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ContactsAddressModel that = (ContactsAddressModel) o;
        return Objects.deepEquals(deliveryPoint, that.deliveryPoint)
                && Objects.equals(city, that.city)
                && Objects.equals(country, that.country)
                && Objects.equals(postalCode, that.postalCode)
                && Objects.equals(administrativeArea, that.administrativeArea);
    }
}
