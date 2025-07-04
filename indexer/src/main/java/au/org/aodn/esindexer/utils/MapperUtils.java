package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.*;
import au.org.aodn.stac.model.ContactsAddressModel;
import au.org.aodn.stac.model.ContactsModel;
import au.org.aodn.stac.model.ContactsPhoneModel;
import au.org.aodn.stac.model.LinkModel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

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
        return safeGet(() -> ciResponsibility.getRole().getCIRoleCode().getCodeListValue().trim())
                .filter(role -> !role.isEmpty())
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    public static String mapContactsOrganization(CIOrganisationType2 organisation) {
        return safeGet(() -> organisation.getName().getCharacterString().getValue().toString())
                .filter(orgName -> !orgName.trim().isEmpty())
                .orElse(null);
    }

    public static String mapContactsName(CIIndividualPropertyType2 individual) {
        return safeGet(() -> individual.getCIIndividual().getName().getCharacterString().getValue().toString().trim())
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    public static String mapContactsPosition(CIIndividualPropertyType2 individual) {
        return safeGet(() -> individual.getCIIndividual().getPositionName().getCharacterString().getValue().toString().trim())
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    public static String mapContactsName(CIIndividualType2 individual) {
        return safeGet(() -> individual.getName().getCharacterString().getValue().toString().trim())
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    public static String mapContactsPosition(CIIndividualType2 individual) {
        return safeGet(() -> individual.getPositionName().getCharacterString().getValue().toString().trim())
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    /**
     * Attribute will not be there if it is empty, this align with what Elastic handle null or empty field.
     * @param address the CIAddressPropertyType2 object to map
     * @return a ContactsAddressModel non-null object with the address info
     */
    public static ContactsAddressModel mapContactsAddress(CIAddressPropertyType2 address) {
        ContactsAddressModel addressItem = ContactsAddressModel.builder().build();

        safeGet(() -> address.getCIAddress().getDeliveryPoint()).ifPresent((value -> {
            List<String> deliveryPoints = new ArrayList<>();

            value.forEach(deliveryPoint -> {
                safeGet(()-> deliveryPoint.getCharacterString().getValue().toString().trim())
                        .filter(s -> !s.isEmpty())
                        .ifPresent(deliveryPoints::add);
            });

            if (!deliveryPoints.isEmpty()) {
                addressItem.setDeliveryPoint(deliveryPoints);
            }
        }));

        safeGet(() -> address.getCIAddress().getCity().getCharacterString().getValue().toString().trim())
                .filter(city -> !city.isEmpty())
                .ifPresent(addressItem::setCity);

        safeGet(() -> address.getCIAddress().getAdministrativeArea().getCharacterString().getValue().toString().trim())
                .filter(administrativeArea -> !administrativeArea.isEmpty())
                .ifPresent(addressItem::setAdministrativeArea);

        safeGet(() -> address.getCIAddress().getPostalCode().getCharacterString().getValue().toString().trim())
                .filter(postalCode -> !postalCode.isEmpty())
                .ifPresent(addressItem::setPostalCode);

        safeGet(() -> address.getCIAddress().getCountry().getCharacterString().getValue().toString().trim())
                .filter(country -> !country.isEmpty())
                .ifPresent(addressItem::setCountry);

        return addressItem;
    }

    public static String mapContactsEmail(CharacterStringPropertyType electronicMailAddress) {
        return safeGet(() -> electronicMailAddress.getCharacterString().getValue().toString().trim())
                .filter(email -> !email.isEmpty())
                .orElse(null);
    }

    public static LinkModel mapContactsOnlineResource(CIOnlineResourcePropertyType2 onlineResource) {
        LinkModel onlineResourceItem = LinkModel.builder().build();

        safeGet(() -> onlineResource.getCIOnlineResource().getLinkage().getCharacterString().getValue().toString().trim())
                .ifPresent(onlineResourceItem::setHref);

        safeGet(() -> onlineResource.getCIOnlineResource().getName().getCharacterString().getValue().toString().trim())
                .ifPresent(onlineResourceItem::setTitle);

        safeGet(() -> onlineResource.getCIOnlineResource().getProtocol().getCharacterString().getValue().toString().trim())
                .ifPresent(onlineResourceItem::setType);

        return onlineResourceItem;
    }

    public static ContactsPhoneModel mapContactsPhone(CITelephonePropertyType2 phone) {
        ContactsPhoneModel phoneItem = ContactsPhoneModel.builder().build();

        safeGet(() -> phone.getCITelephone().getNumber().getCharacterString().getValue().toString().trim())
                .ifPresent(phoneItem::setValue);

        safeGet(() -> phone.getCITelephone().getNumberType().getCITelephoneTypeCode().getCodeListValue().trim())
                .ifPresent(roleStr -> phoneItem.setRoles(List.of(roleStr)));

        return phoneItem;
    }

    public static String mapLanguagesCode(MDDataIdentificationType i) {
        return safeGet(() -> i.getDefaultLocale().getPTLocale().getValue().getLanguage().getLanguageCode().getCodeListValue().trim())
                .filter(code -> !code.isEmpty())
                .orElse(null);
    }


    public static List<MDDataIdentificationType> findMDDataIdentificationType(MDMetadataType source) {
        // Read the raw XML to understand the structure.
        return source.getIdentificationInfo()
                .stream()
                .filter(f -> f.getAbstractResourceDescription() != null)
                .filter(f -> f.getAbstractResourceDescription().getValue() != null)
                .filter(f -> f.getAbstractResourceDescription().getValue() instanceof MDDataIdentificationType)
                .map(f -> (MDDataIdentificationType) f.getAbstractResourceDescription().getValue())
                .collect(Collectors.toList());
    }

    public static List<MDMetadataScopeType> findMDMetadataScopePropertyType(MDMetadataType source) {
        return source.getMetadataScope()
                .stream()
                .map(MDMetadataScopePropertyType::getMDMetadataScope)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<AbstractLineageInformationPropertyType> findMDResourceLineage(MDMetadataType source) {
        var lineages =  source.getResourceLineage();
        if (lineages == null) {
            return Collections.emptyList();
        }
        return lineages
                .stream()
                .filter(f -> f.getAbstractLineageInformation() != null)
                .filter(f -> f.getAbstractLineageInformation().getValue() != null)
                .collect(Collectors.toList());
    }

    public static List<MDDistributionType> findMDDistributionType(MDMetadataType source) {
        return source.getDistributionInfo()
                .stream()
                .filter(f -> f.getAbstractDistribution() != null)
                .filter(f -> f.getAbstractDistribution().getValue() != null)
                .filter(f -> f.getAbstractDistribution().getValue() instanceof MDDistributionType)
                .map(f -> (MDDistributionType) f.getAbstractDistribution().getValue())
                .collect(Collectors.toList());
    }

    public static List<AbstractResponsibilityPropertyType> findMDContact(MDMetadataType source) {
        return source.getContact()
                .stream()
                .filter(f -> f.getAbstractResponsibility() != null)
                .filter(f -> f.getAbstractResponsibility().getValue() != null)
                .filter(f -> f.getAbstractResponsibility().getValue() instanceof CIResponsibilityType2)
                .collect(Collectors.toList());
    }

    public static List<AbstractTypedDatePropertyType> findMDDateInfo(MDMetadataType source) {
        return source.getDateInfo();
    }


    /**
     * Look into the CIContact XML and extract related info and return as a Contract object. Please modify this function
     if more fields need to be returned.
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
     * @param contactsProperty The CIContactPropertyType2, it will appear in organization or individual contact
     * @return A temp object to hold the contact info
     */
    public static Optional<Contacts> mapContactInfo(List<CIContactPropertyType2> contactsProperty) {
        Contacts contacts = Contacts.builder().build();
        if (contactsProperty != null) {
            contactsProperty.forEach(contact -> {

                // Add addresses: only add if the address is not empty
                safeGet(() -> contact.getCIContact().getAddress())
                        .ifPresent(addresses -> {
                            contacts.getAddresses().addAll(
                                    addresses.stream()
                                            .map(MapperUtils::mapContactsAddress)
                                            .filter((addressModel) -> !addressModel.isEmpty())
                                            .toList()
                            );
                        });

                // Add emails : only add if the email is not null
                safeGet(() -> contact.getCIContact().getAddress())
                        .ifPresent(addresses -> {
                            addresses.forEach(address -> {
                                safeGet(() -> address.getCIAddress().getElectronicMailAddress())
                                        .ifPresent(electronicMailAddress ->
                                                contacts.getEmails().addAll(
                                                        electronicMailAddress
                                                                .stream()
                                                                .map(MapperUtils::mapContactsEmail)
                                                                .filter(Objects::nonNull)
                                                                .toList())
                                        );
                            });
                        });

                // Add phone number: only add if the phoneModel is not null
                safeGet(() -> contact.getCIContact().getPhone())
                        .ifPresent(phones -> {
                            contacts.getPhones()
                                    .addAll(phones.stream()
                                            .map(MapperUtils::mapContactsPhone)
                                            .filter(Objects::nonNull)
                                            .toList());
                        });

                // Online resources: only add if the linkModel is not null
                safeGet(() -> contact.getCIContact().getOnlineResource())
                        .ifPresent(onlineResource ->
                                contacts.getOnlineResources()
                                        .addAll(onlineResource
                                                .stream()
                                                .map(MapperUtils::mapContactsOnlineResource)
                                                .filter(Objects::nonNull)
                                                .toList())
                        );
            });
        }
        return Optional.of(contacts);
    }



    public static List<ContactsModel> mapContactsFromOrg(CIResponsibilityType2 ciResponsibility, CIOrganisationType2 organisation) {
        // If the organisation has no individual contacts, just create organisation contacts model
        if (safeGet(organisation::getIndividual).map(List::isEmpty).orElse(true)) {
            Optional<Contacts> orgContactInfo = mapContactInfo(organisation.getContactInfo());
            ContactsModel orgContactsModel = ContactsModel
                    .builder()
                    .roles(mapContactsRole(ciResponsibility))
                    .organization(mapContactsOrganization(organisation))
                    .addresses(orgContactInfo.map(Contacts::getAddresses).orElse(null))
                    .emails(orgContactInfo.map(Contacts::getEmails).orElse(null))
                    .phones(orgContactInfo.map(Contacts::getPhones).orElse(null))
                    .links(orgContactInfo.map(Contacts::getOnlineResources).orElse(null))
                    .build();

            return List.of(orgContactsModel);
        }
        return organisation
                .getIndividual()
                .stream()
                .map(individual -> {
                    Optional<Contacts> indvContactInfo = mapContactInfo(individual.getCIIndividual().getContactInfo());
                    Optional<Contacts> orgContactInfo = mapContactInfo(organisation.getContactInfo());
                    ContactsModel contactsModel = ContactsModel
                            .builder()
                            .name(mapContactsName(individual))
                            .position(mapContactsPosition(individual))
                            .roles(mapContactsRole(ciResponsibility))
                            .organization(mapContactsOrganization(organisation))
                            .build();

                    // Set the contact info from individual or organisation
                    // Always prefer individual contact info over organisation contact info
                    contactsModel.setAddresses(
                            indvContactInfo.map(Contacts::getAddresses)
                                    .filter(addr -> !addr.isEmpty())
                                    .orElse(orgContactInfo.map(Contacts::getAddresses).orElse(null))
                    );

                    contactsModel.setEmails(
                            indvContactInfo.map(Contacts::getEmails)
                                    .filter(email -> !email.isEmpty())
                                    .orElse(orgContactInfo.map(Contacts::getEmails).orElse(null))
                    );

                    contactsModel.setPhones(
                            indvContactInfo.map(Contacts::getPhones)
                                    .filter(phone -> !phone.isEmpty())
                                    .orElse(orgContactInfo.map(Contacts::getPhones).orElse(null))
                    );

                    contactsModel.setLinks(
                            indvContactInfo.map(Contacts::getOnlineResources)
                                    .filter(link -> !link.isEmpty())
                                    .orElse(orgContactInfo.map(Contacts::getOnlineResources).orElse(null))
                    );

                    return contactsModel;
                })
                .toList();
    }

    public static List<ContactsModel> mapContactsFromIndividual(CIResponsibilityType2 ciResponsibility, CIIndividualType2 individual) {
        Optional<Contacts> indvContactInfo = mapContactInfo(individual.getContactInfo());
        ContactsModel indvContactsModel = ContactsModel
                .builder()
                .roles(mapContactsRole(ciResponsibility))
                .name(mapContactsName(individual))
                .position(mapContactsPosition(individual))
                .addresses(indvContactInfo.map(Contacts::getAddresses).orElse(null))
                .emails(indvContactInfo.map(Contacts::getEmails).orElse(null))
                .phones(indvContactInfo.map(Contacts::getPhones).orElse(null))
                .links(indvContactInfo.map(Contacts::getOnlineResources).orElse(null))
                .build();
        return List.of(indvContactsModel);
    }

    public static List<ContactsModel> mapPartyContacts(CIResponsibilityType2 ciResponsibility, AbstractCIPartyPropertyType2 party) {
        List<ContactsModel> results = new ArrayList<>();
        if (party.getAbstractCIParty() != null && party.getAbstractCIParty().getValue() != null) {
            if (party.getAbstractCIParty().getValue() instanceof CIOrganisationType2 organisation) {
                results.addAll(mapContactsFromOrg(ciResponsibility, organisation));
            } else if (party.getAbstractCIParty().getValue() instanceof CIIndividualType2 individual) {
                results.addAll(mapContactsFromIndividual(ciResponsibility, individual));
            }
        }
        return results;
    }

    public static List<ContactsModel> addRoleToContacts(List<ContactsModel> contacts, String role) {
        contacts.forEach(contact -> {
            var roles = new ArrayList<String>();
            if (contact.getRoles() != null) {
                roles.addAll(contact.getRoles());
            }
            roles.add(role);
            contact.setRoles(roles);
        });
        return contacts;
    }


}
