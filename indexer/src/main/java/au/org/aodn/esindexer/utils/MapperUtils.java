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
        return safeGet(() -> ciResponsibility.getRole().getCIRoleCode().getCodeListValue())
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    public static String mapContactsOrganization(AbstractCIPartyPropertyType2 party) {
        return safeGet(() -> party.getAbstractCIParty().getValue().getName().getCharacterString().getValue().toString())
                .orElse("");
    }

    public static String mapContactsName(CIIndividualPropertyType2 individual) {
        return safeGet(() -> individual.getCIIndividual().getName().getCharacterString().getValue().toString())
                .orElse("");
    }

    public static String mapContactsPosition(CIIndividualPropertyType2 individual) {
        return safeGet(() -> individual.getCIIndividual().getPositionName().getCharacterString().getValue().toString())
                .orElse("");
    }

    public static String mapContactsName(CIIndividualType2 individual) {
        return safeGet(() -> individual.getName().getCharacterString().getValue().toString())
                .orElse("");
    }

    public static String mapContactsPosition(CIIndividualType2 individual) {
        return safeGet(() -> individual.getPositionName().getCharacterString().getValue().toString())
                .orElse("");
    }

    /**
     * Attribute will not be there if it is empty, this align with what Elastic handle null or empty field.
     * @param address
     * @return
     */
    public static ContactsAddressModel mapContactsAddress(CIAddressPropertyType2 address) {
        ContactsAddressModel addressItem = ContactsAddressModel.builder().build();

        safeGet(() -> address.getCIAddress().getDeliveryPoint()).ifPresent((value -> {
            List<String> deliveryPoints = new ArrayList<>();

            value.forEach(deliveryPoint -> {
                String deliveryPointString = deliveryPoint.getCharacterString().getValue().toString();
                deliveryPoints.add(deliveryPointString != null ? deliveryPointString : "");
            });

            if (!deliveryPoints.isEmpty()) {
                addressItem.setDeliveryPoint(deliveryPoints);
            }
        }));

        safeGet(() -> address.getCIAddress().getCity().getCharacterString().getValue().toString())
                .ifPresent(addressItem::setCity);

        safeGet(() -> address.getCIAddress().getAdministrativeArea().getCharacterString().getValue().toString())
                .ifPresent(addressItem::setAdministrativeArea);

        safeGet(() -> address.getCIAddress().getPostalCode().getCharacterString().getValue().toString())
                .ifPresent(addressItem::setPostalCode);

        safeGet(() -> address.getCIAddress().getCountry().getCharacterString().getValue().toString())
                .ifPresent(addressItem::setCountry);

        return addressItem;
    }

    public static String mapContactsEmail(CharacterStringPropertyType electronicMailAddress) {
        Optional<String> email = safeGet(() -> electronicMailAddress.getCharacterString().getValue().toString());

        if (email.isPresent() && !email.get().trim().isEmpty()) {
            return email.get();
        }
        return null;
    }

    public static LinkModel mapContactsOnlineResource(CIOnlineResourcePropertyType2 onlineResource) {
        LinkModel onlineResourceItem = LinkModel.builder().build();

        safeGet(() -> onlineResource.getCIOnlineResource().getLinkage().getCharacterString().getValue().toString())
                .ifPresent(onlineResourceItem::setHref);

        safeGet(() -> onlineResource.getCIOnlineResource().getName().getCharacterString().getValue().toString())
                .ifPresent(onlineResourceItem::setTitle);

        safeGet(() -> onlineResource.getCIOnlineResource().getProtocol().getCharacterString().getValue().toString())
                .ifPresent(onlineResourceItem::setType);

        return onlineResourceItem;
    }

    public static ContactsPhoneModel mapContactsPhone(CITelephonePropertyType2 phone) {
        ContactsPhoneModel phoneItem = ContactsPhoneModel.builder().build();

        safeGet(() -> phone.getCITelephone().getNumber().getCharacterString().getValue().toString())
                .ifPresent(phoneItem::setValue);

        safeGet(() -> phone.getCITelephone().getNumberType().getCITelephoneTypeCode().getCodeListValue())
                .ifPresent(roleStr -> phoneItem.setRoles(List.of(roleStr)));

        return phoneItem;
    }

    public static String mapLanguagesCode(MDDataIdentificationType i) {
        try {
            return i.getDefaultLocale().getPTLocale().getValue().getLanguage().getLanguageCode().getCodeListValue();
        } catch (NullPointerException e) {
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

                // Add addresses
                safeGet(() -> contact.getCIContact().getAddress())
                        .ifPresent(addresses -> {
                            addresses.forEach(address -> {
                                ContactsAddressModel addressModel = mapContactsAddress(address);
                                if (!addressModel.isEmpty()) {
                                    contacts.getAddresses().add(addressModel);
                                }

                            });
                        });

                // Add emails
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

                // Add phone number
                safeGet(() -> contact.getCIContact().getPhone())
                        .ifPresent(phone ->
                                contacts.getPhones()
                                        .addAll(phone.stream().map(MapperUtils::mapContactsPhone).toList())
                        );

                // Online resources
                safeGet(() -> contact.getCIContact().getOnlineResource())
                        .ifPresent(onlineResource ->
                                contacts.getOnlineResources()
                                        .addAll(onlineResource.stream().map(MapperUtils::mapContactsOnlineResource).toList())
                        );
            });
        }
        return Optional.of(contacts);
    }

    public static List<ContactsModel> mapContactsFromOrg(CIResponsibilityType2 ciResponsibility, CIOrganisationType2 organisation) {
        Optional<Contacts> orgContacts = mapContactInfo(organisation.getContactInfo());

        if (organisation.getIndividual() == null || organisation.getIndividual().isEmpty()) {
            ContactsModel orgContactsModel = ContactsModel.builder().build();
            orgContactsModel.setRoles(MapperUtils.mapContactsRole(ciResponsibility));
            orgContactsModel.setOrganization(organisation.getName().getCharacterString().getValue().toString());
            orgContactsModel.setName("");
            orgContactsModel.setPosition("");

            orgContacts.ifPresent(o -> {
                orgContactsModel.setAddresses(o.getAddresses());
                orgContactsModel.setEmails(o.getEmails());
                orgContactsModel.setPhones(o.getPhones());
                orgContactsModel.setLinks(o.getOnlineResources());
            });
            return List.of(orgContactsModel);
        }
        return organisation
                .getIndividual()
                .stream()
                .map(individual -> {
                    ContactsModel contactsModel = ContactsModel.builder().build();
                    contactsModel.setName(mapContactsName(individual));
                    contactsModel.setPosition(mapContactsPosition(individual));
                    contactsModel.setRoles(mapContactsRole(ciResponsibility));

                    safeGet(() -> organisation.getName().getCharacterString().getValue().toString())
                            .ifPresentOrElse(contactsModel::setOrganization, () -> contactsModel.setOrganization(""));

                    Optional<Contacts> individualContacts = mapContactInfo(individual.getCIIndividual().getContactInfo());

                    // Always prefer individual contact info, fall back to org if individual is empty
                    contactsModel.setAddresses(
                            individualContacts.map(Contacts::getAddresses)
                                    .filter(addresses -> !addresses.isEmpty())
                                    .orElse(orgContacts.map(Contacts::getAddresses).orElse(new LinkedHashSet<>()))
                    );

                    contactsModel.setEmails(
                            individualContacts.map(Contacts::getEmails)
                                    .filter(emails -> !emails.isEmpty())
                                    .orElse(orgContacts.map(Contacts::getEmails).orElse(new LinkedHashSet<>()))
                    );

                    contactsModel.setPhones(
                            individualContacts.map(Contacts::getPhones)
                                    .filter(phones -> !phones.isEmpty())
                                    .orElse(orgContacts.map(Contacts::getPhones).orElse(new LinkedHashSet<>()))
                    );

                    contactsModel.setLinks(
                            individualContacts.map(Contacts::getOnlineResources)
                                    .filter(links -> !links.isEmpty())
                                    .orElse(orgContacts.map(Contacts::getOnlineResources).orElse(new LinkedHashSet<>()))
                    );

                    return contactsModel;
                })
                .toList();
    }

    public static List<ContactsModel> mapContactsFromIndividual(CIResponsibilityType2 ciResponsibility, CIIndividualType2 individual) {
        ContactsModel indvContactsModel = ContactsModel.builder().build();
        indvContactsModel.setName(mapContactsName(individual));
        indvContactsModel.setPosition(mapContactsPosition(individual));
        indvContactsModel.setRoles(mapContactsRole(ciResponsibility));
        indvContactsModel.setOrganization("");
        Optional<Contacts> indvContactInfo = mapContactInfo(individual.getContactInfo());

        indvContactInfo.ifPresent(i -> {
            indvContactsModel.setAddresses(i.getAddresses());
            indvContactsModel.setEmails(i.getEmails());
            indvContactsModel.setPhones(i.getPhones());
            indvContactsModel.setLinks(i.getOnlineResources());
        });

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
