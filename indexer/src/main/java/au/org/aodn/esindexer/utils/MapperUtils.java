package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.*;
import au.org.aodn.stac.model.ContactsAddressModel;
import au.org.aodn.stac.model.ContactsPhoneModel;
import au.org.aodn.stac.model.LinkModel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

public class MapperUtils {

    @Getter
    @Setter
    @Builder
    public static class Contacts {

        @Builder.Default
        protected LinkedHashSet<ContactsAddressModel> addresses = new LinkedHashSet<>();

        @Builder.Default
        protected LinkedHashSet<ContactsPhoneModel> phones = new LinkedHashSet<>();

        @Builder.Default
        protected LinkedHashSet<String> emails = new LinkedHashSet<>();

        @Builder.Default
        protected LinkedHashSet<LinkModel> onlineResources = new LinkedHashSet<>();
    }

    public static List<String> mapContactsRole(CIResponsibilityType2 ciResponsibility) {
        if (
                ciResponsibility == null
                || ciResponsibility.getRole() == null
                || ciResponsibility.getRole().getCIRoleCode() == null
                || ciResponsibility.getRole().getCIRoleCode().getCodeListValue() == null
        ) {
            return Collections.emptyList();
        }
        return Collections.singletonList(ciResponsibility.getRole().getCIRoleCode().getCodeListValue());
    }

    public static String mapContactsOrganization(AbstractCIPartyPropertyType2 party) {
        String organisationString = "";
        if (party.getAbstractCIParty() != null) {
            if (party.getAbstractCIParty().getValue().getName().getCharacterString() != null) {
                organisationString = party.getAbstractCIParty().getValue().getName().getCharacterString().getValue().toString();
            }
        }
        return organisationString;
    }

    public static String mapContactsName(CIIndividualPropertyType2 individual) {
        CharacterStringPropertyType nameString = individual.getCIIndividual().getName();
        return nameString != null ?
                individual.getCIIndividual().getName().getCharacterString().getValue().toString() : "";
    }

    public static String mapContactsPosition(CIIndividualPropertyType2 individual) {
        CharacterStringPropertyType positionString = individual.getCIIndividual().getPositionName();
        return positionString != null ?
                individual.getCIIndividual().getPositionName().getCharacterString().getValue().toString() : "";
    }
    /**
     * Attribute will not be there if it is empty, this align with what Elastic handle null or empty field.
     * @param address
     * @return
     */
    public static ContactsAddressModel mapContactsAddress(CIAddressPropertyType2 address) {
        ContactsAddressModel addressItem = ContactsAddressModel.builder().build();
        List<String> deliveryPoints = new ArrayList<>();

        address.getCIAddress().getDeliveryPoint().forEach(deliveryPoint -> {
            String deliveryPointString = deliveryPoint.getCharacterString().getValue().toString();
            deliveryPoints.add(deliveryPointString != null ? deliveryPointString : "");
        });

        if(!deliveryPoints.isEmpty()) {
            addressItem.setDeliveryPoint(deliveryPoints);
        }

        CharacterStringPropertyType cityString = address.getCIAddress().getCity();
        if(cityString != null
                && cityString.getCharacterString() != null
                && cityString.getCharacterString().getValue() != null) {

            addressItem.setCity(cityString.getCharacterString().getValue().toString());
        }

        CharacterStringPropertyType administrativeAreaString = address.getCIAddress().getAdministrativeArea();
        if(administrativeAreaString != null
                && administrativeAreaString.getCharacterString() != null
                && administrativeAreaString.getCharacterString().getValue() != null) {
            addressItem.setAdministrativeArea(administrativeAreaString.getCharacterString().getValue().toString());
        }

        CharacterStringPropertyType postalCodeString = address.getCIAddress().getPostalCode();
        if(postalCodeString != null
                && postalCodeString.getCharacterString() != null
                && postalCodeString.getCharacterString().getValue() != null) {
            addressItem.setPostalCode(postalCodeString.getCharacterString().getValue().toString());
        }

        CharacterStringPropertyType countryString = address.getCIAddress().getCountry();
        if(countryString != null
                && countryString.getCharacterString() != null
                && countryString.getCharacterString().getValue() != null) {
            addressItem.setCountry(countryString.getCharacterString().getValue().toString());
        }

        return addressItem;
    }

    public static String mapContactsEmail(CharacterStringPropertyType electronicMailAddress) {

        if(electronicMailAddress != null
                && electronicMailAddress.getCharacterString() != null
                && electronicMailAddress.getCharacterString().getValue() != null
                && !"".equalsIgnoreCase(electronicMailAddress.getCharacterString().getValue().toString())) {
            return electronicMailAddress.getCharacterString().getValue().toString();
        }
        else {
            return null;
        }
    }

    public static LinkModel mapContactsOnlineResource(CIOnlineResourcePropertyType2 onlineResource) {
        LinkModel onlineResourceItem = LinkModel.builder().build();

        CharacterStringPropertyType linkString = onlineResource.getCIOnlineResource().getLinkage();
        if(linkString != null
                && linkString.getCharacterString() != null
                && linkString.getCharacterString().getValue() != null) {
            onlineResourceItem.setHref(linkString.getCharacterString().getValue().toString());
        }

        CharacterStringPropertyType resourceNameString = onlineResource.getCIOnlineResource().getName();
        if(resourceNameString != null
                && resourceNameString.getCharacterString() != null
                && resourceNameString.getCharacterString().getValue() != null) {
            onlineResourceItem.setTitle(resourceNameString.getCharacterString().getValue().toString());
        }

        CharacterStringPropertyType linkTypeString = onlineResource.getCIOnlineResource().getProtocol();
        if(linkTypeString != null
                && linkTypeString.getCharacterString() != null
                && linkTypeString.getCharacterString().getValue() != null) {
            onlineResourceItem.setType(linkTypeString.getCharacterString().getValue().toString());
        }

        return onlineResourceItem;
    }

    public static ContactsPhoneModel mapContactsPhone(CITelephonePropertyType2 phone) {
        ContactsPhoneModel phoneItem = ContactsPhoneModel.builder().build();

        CharacterStringPropertyType phoneString = phone.getCITelephone().getNumber();
        if(phoneString != null
                && phoneString.getCharacterString() != null
                && phoneString.getCharacterString().getValue() != null) {

            phoneItem.setValue(phoneString.getCharacterString().getValue().toString());
        }

        CodeListValueType phoneCode = phone.getCITelephone().getNumberType().getCITelephoneTypeCode();
        if(phoneCode != null && phoneCode.getCodeListValue() != null && !phoneCode.getCodeListValue().isEmpty()) {
            phoneItem.setRoles(List.of(phoneCode.getCodeListValue()));
        }

        return phoneItem;
    }

    public static String mapLanguagesCode(MDDataIdentificationType i) {
        try {
            return i.getDefaultLocale().getPTLocale().getValue().getLanguage().getLanguageCode().getCodeListValue();
        }
        catch (NullPointerException e) {
            return null;
        }
    }

    public static List<MDDataIdentificationType> findMDDataIdentificationType(MDMetadataType source) {
        // Read the raw XML to understand the structure.
        return source.getIdentificationInfo()
                .stream()
                .filter(f -> f.getAbstractResourceDescription() != null)
                .filter(f -> f.getAbstractResourceDescription().getValue() != null)
                .filter(f -> f.getAbstractResourceDescription().getValue() instanceof MDDataIdentificationType)
                .map(f -> (MDDataIdentificationType)f.getAbstractResourceDescription().getValue())
                .collect(Collectors.toList());
    }

    public static List<MDMetadataScopeType> findMDMetadataScopePropertyType(MDMetadataType source) {
        return source.getMetadataScope()
                .stream()
                .map(MDMetadataScopePropertyType::getMDMetadataScope)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<MDDistributionType> findMDDistributionType(MDMetadataType source) {
        return source.getDistributionInfo()
                .stream()
                .filter(f -> f.getAbstractDistribution() != null)
                .filter(f -> f.getAbstractDistribution().getValue() != null)
                .filter(f -> f.getAbstractDistribution().getValue() instanceof MDDistributionType)
                .map(f -> (MDDistributionType)f.getAbstractDistribution().getValue())
                .collect(Collectors.toList());
    }
    /**
     * Look into the CIContact XML and extract related info and return as a Contract object. Please modify this function
     * if more fields need to be returned.
     *   <mdb:contact>
     *     <cit:CI_Responsibility>
     *       <cit:role>
     *         <cit:CI_RoleCode codeList="http://schemas.aodn.org.au/mcp-3.0/codelists.xml#CI_RoleCode" codeListValue="pointOfContact">pointOfContact</cit:CI_RoleCode>
     *       </cit:role>
     *       <cit:party>
     *         <cit:CI_Organisation xsi:schemaLocation="http://standards.iso.org/iso/19115/-3/mds/2.0 http://standards.iso.org/iso/19115/-3/mds/2.0/mds.xsd">
     *           <cit:name>
     *             <gco:CharacterString xsi:schemaLocation="http://standards.iso.org/iso/19115/-3/mds/2.0 http://standards.iso.org/iso/19115/-3/mds/2.0/mds.xsd">CSIRO Oceans &amp; Atmosphere - Hobart</gco:CharacterString>
     *           </cit:name>
     *           <cit:contactInfo>
     *             <cit:CI_Contact xsi:schemaLocation="http://standards.iso.org/iso/19115/-3/mds/2.0 http://standards.iso.org/iso/19115/-3/mds/2.0/mds.xsd">
     *               <cit:phone>
     *                 <cit:CI_Telephone>
     *                   <cit:number>
     *                     <gco:CharacterString>+61 3 6232 5222</gco:CharacterString>
     *                   </cit:number>
     *                   <cit:numberType>
     *                     <cit:CI_TelephoneTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelist/cat/codelists.xml#CI_RoleCode" codeListValue="voice" />
     *                   </cit:numberType>
     *                 </cit:CI_Telephone>
     *               </cit:phone>
     *               <cit:phone>
     *                 <cit:CI_Telephone>
     *                   <cit:number>
     *                     <gco:CharacterString>+61 3 6232 5000</gco:CharacterString>
     *                   </cit:number>
     *                   <cit:numberType>
     *                     <cit:CI_TelephoneTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelist/cat/codelists.xml#CI_RoleCode" codeListValue="facsimile" />
     *                   </cit:numberType>
     *                 </cit:CI_Telephone>
     *               </cit:phone>
     *               <cit:address>
     *                 <cit:CI_Address>
     *                   <cit:deliveryPoint>
     *                     <gco:CharacterString>GPO Box 1538</gco:CharacterString>
     *                   </cit:deliveryPoint>
     *                   <cit:city>
     *                     <gco:CharacterString>Hobart</gco:CharacterString>
     *                   </cit:city>
     *                   <cit:administrativeArea>
     *                     <gco:CharacterString>TAS</gco:CharacterString>
     *                   </cit:administrativeArea>
     *                   <cit:postalCode>
     *                     <gco:CharacterString>7001</gco:CharacterString>
     *                   </cit:postalCode>
     *                   <cit:country>
     *                     <gco:CharacterString>Australia</gco:CharacterString>
     *                   </cit:country>
     *                   <cit:electronicMailAddress gco:nilReason="missing">
     *                     <gco:CharacterString />
     *                   </cit:electronicMailAddress>
     *                 </cit:CI_Address>
     *               </cit:address>
     *               <cit:onlineResource>
     *                 <cit:CI_OnlineResource>
     *                   <cit:linkage>
     *                     <gco:CharacterString>http://www.csiro.au/en/Research/OandA</gco:CharacterString>
     *                   </cit:linkage>
     *                   <cit:protocol>
     *                     <gco:CharacterString>WWW:LINK-1.0-http--link</gco:CharacterString>
     *                   </cit:protocol>
     *                   <cit:description>
     *                     <gco:CharacterString>Web address for organisation CSIRO Oceans &amp; Atmosphere - Hobart</gco:CharacterString>
     *                   </cit:description>
     *                 </cit:CI_OnlineResource>
     *               </cit:onlineResource>
     *             </cit:CI_Contact>
     *           </cit:contactInfo>
     *           <cit:contactInfo>
     *             <cit:CI_Contact xsi:schemaLocation="http://standards.iso.org/iso/19115/-3/mds/2.0 http://standards.iso.org/iso/19115/-3/mds/2.0/mds.xsd">
     *               <cit:phone>
     *                 <cit:CI_Telephone>
     *                   <cit:number>
     *                     <gco:CharacterString>+61 3 6232 5222</gco:CharacterString>
     *                   </cit:number>
     *                   <cit:numberType>
     *                     <cit:CI_TelephoneTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelist/cat/codelists.xml#CI_RoleCode" codeListValue="voice" />
     *                   </cit:numberType>
     *                 </cit:CI_Telephone>
     *               </cit:phone>
     *               <cit:phone>
     *                 <cit:CI_Telephone>
     *                   <cit:number>
     *                     <gco:CharacterString>+61 3 6232 5000</gco:CharacterString>
     *                   </cit:number>
     *                   <cit:numberType>
     *                     <cit:CI_TelephoneTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelist/cat/codelists.xml#CI_RoleCode" codeListValue="facsimile" />
     *                   </cit:numberType>
     *                 </cit:CI_Telephone>
     *               </cit:phone>
     *               <cit:address>
     *                 <cit:CI_Address>
     *                   <cit:deliveryPoint>
     *                     <gco:CharacterString>Castray Esplanade</gco:CharacterString>
     *                   </cit:deliveryPoint>
     *                   <cit:city>
     *                     <gco:CharacterString>Hobart</gco:CharacterString>
     *                   </cit:city>
     *                   <cit:administrativeArea>
     *                     <gco:CharacterString>TAS</gco:CharacterString>
     *                   </cit:administrativeArea>
     *                   <cit:postalCode>
     *                     <gco:CharacterString>7000</gco:CharacterString>
     *                   </cit:postalCode>
     *                   <cit:country>
     *                     <gco:CharacterString>Australia</gco:CharacterString>
     *                   </cit:country>
     *                   <cit:electronicMailAddress gco:nilReason="missing">
     *                     <gco:CharacterString />
     *                   </cit:electronicMailAddress>
     *                 </cit:CI_Address>
     *               </cit:address>
     *               <cit:onlineResource>
     *                 <cit:CI_OnlineResource>
     *                   <cit:linkage>
     *                     <gco:CharacterString>http://www.csiro.au/en/Research/OandA</gco:CharacterString>
     *                   </cit:linkage>
     *                   <cit:protocol>
     *                     <gco:CharacterString>WWW:LINK-1.0-http--link</gco:CharacterString>
     *                   </cit:protocol>
     *                   <cit:description>
     *                     <gco:CharacterString>Web address for organisation CSIRO Oceans &amp; Atmosphere - Hobart</gco:CharacterString>
     *                   </cit:description>
     *                 </cit:CI_OnlineResource>
     *               </cit:onlineResource>
     *             </cit:CI_Contact>
     *           </cit:contactInfo>
     *           <cit:individual>
     *             <cit:CI_Individual>
     *               <cit:name>
     *                 <gco:CharacterString>CSIRO O&amp;A, Information &amp; Data Centre</gco:CharacterString>
     *               </cit:name>
     *               <cit:contactInfo>
     *                 <cit:CI_Contact>
     *                   <cit:address>
     *                     <cit:CI_Address>
     *                       <cit:electronicMailAddress>
     *                         <gco:CharacterString>data-requests-hf@csiro.au</gco:CharacterString>
     *                       </cit:electronicMailAddress>
     *                     </cit:CI_Address>
     *                   </cit:address>
     *                 </cit:CI_Contact>
     *               </cit:contactInfo>
     *               <cit:positionName>
     *                 <gco:CharacterString>Data Requests</gco:CharacterString>
     *               </cit:positionName>
     *             </cit:CI_Individual>
     *           </cit:individual>
     *         </cit:CI_Organisation>
     *       </cit:party>
     *     </cit:CI_Responsibility>
     *   </mdb:contact>
     *
     * @param contacts The CIContactPropertyType2, it will appear in organization or individual contact
     * @return A temp object to hold the contact info
     */
    public static Optional<Contacts> mapContactInfo(List<CIContactPropertyType2> contacts) {
        if(contacts == null) {
            return Optional.empty();
        }
        else {
            Contacts c = Contacts.builder().build();
            contacts.forEach(contact -> {
                // Add all address of organization
                if (contact.getCIContact() != null && contact.getCIContact().getAddress() != null) {
                    contact.getCIContact().getAddress().forEach(v -> {
                        ContactsAddressModel address = MapperUtils.mapContactsAddress(v);
                        if(!address.isEmpty()) {
                            c.getAddresses().add(address);

                            if (v.getCIAddress() != null && v.getCIAddress().getElectronicMailAddress() != null) {
                                c.getEmails().addAll(
                                        v.getCIAddress()
                                                .getElectronicMailAddress()
                                                .stream()
                                                .map(MapperUtils::mapContactsEmail)
                                                .filter(Objects::nonNull)
                                                .toList());
                            }
                        }
                    });
                }
                // Add phone number of organization
                if (contact.getCIContact() != null && contact.getCIContact().getPhone() != null) {
                    c.getPhones().addAll(contact.getCIContact().getPhone().stream().map(MapperUtils::mapContactsPhone).toList());
                }
                // Online resources
                if (contact.getCIContact().getOnlineResource() != null) {
                    c.getOnlineResources().addAll(contact.getCIContact().getOnlineResource().stream().map(MapperUtils::mapContactsOnlineResource).toList());
                }
            });

            return Optional.of(c);
        }
    }

}
