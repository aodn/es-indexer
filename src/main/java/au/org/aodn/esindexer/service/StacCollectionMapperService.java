package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.MappingValueException;
import au.org.aodn.esindexer.model.*;
import au.org.aodn.esindexer.utils.BBoxUtils;
import au.org.aodn.esindexer.utils.GeometryUtils;

import au.org.aodn.esindexer.utils.TemporalUtils;
import au.org.aodn.metadata.iso19115_3_2018.*;
import jakarta.xml.bind.JAXBElement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class transform the XML from GeoNetwork to STAC format and store it into Elastic-Search
 */
@Service
@Mapper(componentModel = "spring")
public abstract class StacCollectionMapperService {

    public static final String REAL_TIME = "real-time";

    @Mapping(target="uuid", source = "source", qualifiedByName = "mapUUID")
    @Mapping(target="title", source = "source", qualifiedByName = "mapTitle" )
    @Mapping(target="description", source = "source", qualifiedByName = "mapDescription")
    @Mapping(target="summaries.status", source = "source", qualifiedByName = "mapSummaries.status")
    @Mapping(target="summaries.scope", source = "source", qualifiedByName = "mapSummaries.scope")
    @Mapping(target="summaries.geometry", source = "source", qualifiedByName = "mapSummaries.geometry")
    @Mapping(target="summaries.temporal", source = "source", qualifiedByName = "mapSummaries.temporal")
    @Mapping(target="summaries.updateFrequency", source = "source", qualifiedByName = "mapSummaries.updateFrequency")
    @Mapping(target="summaries.datasetProvider", source = "source", qualifiedByName = "mapSummaries.datasetProvider")
    @Mapping(target="extent.bbox", source = "source", qualifiedByName = "mapExtentBbox")
    @Mapping(target="extent.temporal", source = "source", qualifiedByName = "mapExtentTemporal")
    @Mapping(target="contacts", source = "source", qualifiedByName = "mapContacts")
    @Mapping(target="themes", source = "source", qualifiedByName = "mapThemes")
    @Mapping(target="languages", source = "source", qualifiedByName = "mapLanguages")
    @Mapping(target="links", source = "source", qualifiedByName = "mapLinks")
    @Mapping(target="license", source = "source", qualifiedByName = "mapLicense")
    @Mapping(target="providers", source = "source", qualifiedByName = "mapProviders")
    public abstract StacCollectionModel mapToSTACCollection(MDMetadataType source);


    private static final Logger logger = LoggerFactory.getLogger(StacCollectionMapperService.class);

    @Value("${spring.jpa.properties.hibernate.jdbc.time_zone}")
    private String timeZoneId;

    @Named("mapUUID")
    String mapUUID(MDMetadataType source) {
        return source.getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue().toString();
    }

    @Named("mapExtentBbox")
    List<List<BigDecimal>> mapExtentBbox(MDMetadataType source) {
        return createGeometryItems(
                source,
                BBoxUtils::createBBoxFromEXBoundingPolygonType,
                BBoxUtils::createBBoxFromEXGeographicBoundingBoxType
        );
    }

    @Named("mapExtentTemporal")
    List<String[]> mapExtentTemporal(MDMetadataType source) {
        return TemporalUtils.concatOverallTemporalRange(createExtentTemporal(source));
    }

    List<String[]> createExtentTemporal(MDMetadataType source) {

        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if (items.isEmpty()) {
            logger.warn("Unable to find extent temporal information for metadata record: " + this.mapUUID(source));
            return null;
        }

        List<String[]> result = new ArrayList<>();
        for (MDDataIdentificationType i : items) {
            i.getExtent().forEach(extent -> {
                if (extent.getAbstractExtent().getValue() instanceof EXExtentType exExtentType) {

                    exExtentType.getTemporalElement().forEach(temporalElement -> {

                        String[] temporalPair = new String[2];
                        temporalPair[0] = null;
                        temporalPair[1] = null;

                        EXTemporalExtentType exTemporalExtent = temporalElement.getEXTemporalExtent().getValue();
                        if (exTemporalExtent != null) {
                            AbstractTimePrimitiveType abstractTimePrimitive = exTemporalExtent.getExtent().getAbstractTimePrimitive().getValue();
                            if (abstractTimePrimitive instanceof TimePeriodType timePeriodType) {


                                if (timePeriodType.getBegin() != null) {
                                    if (timePeriodType.getBegin().getTimeInstant() != null) {
                                        if (timePeriodType.getBegin().getTimeInstant().getTimePosition() != null) {
                                           if (!timePeriodType.getBegin().getTimeInstant().getTimePosition().getValue().isEmpty()) {
                                               temporalPair[0] = convertDateToZonedDateTime(timePeriodType.getBegin().getTimeInstant().getTimePosition().getValue().get(0));
                                           }
                                        }
                                    }
                                } else {
                                    if (!timePeriodType.getBeginPosition().getValue().isEmpty()) {
                                        temporalPair[0] = convertDateToZonedDateTime(timePeriodType.getBeginPosition().getValue().get(0));
                                    }
                                }

                                if (timePeriodType.getEnd() != null) {
                                    if (timePeriodType.getEnd().getTimeInstant() != null) {
                                        if (timePeriodType.getEnd().getTimeInstant().getTimePosition() != null) {
                                            if (!timePeriodType.getEnd().getTimeInstant().getTimePosition().getValue().isEmpty()) {
                                                temporalPair[1] = convertDateToZonedDateTime(timePeriodType.getEnd().getTimeInstant().getTimePosition().getValue().get(0));
                                            }
                                        }
                                    }
                                } else {
                                    if (!timePeriodType.getEndPosition().getValue().isEmpty()) {
                                        temporalPair[1] = convertDateToZonedDateTime(timePeriodType.getEndPosition().getValue().get(0));
                                    }
                                }
                            }

                            result.add(temporalPair);
                        }
                    });
                }
            });
        }
        return result;
    }

    private String convertDateToZonedDateTime(String inputDateString) {
        try {
            String inputDateTimeString = inputDateString;
            if (!inputDateString.contains("T")) {
                inputDateTimeString += "T00:00:00";
            }

            ZonedDateTime zonedDateTime = ZonedDateTime.parse(inputDateTimeString, TemporalUtils.TIME_FORMATTER.withZone(ZoneId.of(timeZoneId)));

            // Convert to UTC
            ZonedDateTime utcZonedDateTime = zonedDateTime.withZoneSameInstant(ZoneOffset.UTC);

            DateTimeFormatter outputFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

            return utcZonedDateTime.format(outputFormatter);
        } catch (Exception e) {
            logger.warn("Unable to convert date to ISO_OFFSET_DATE_TIME: " + inputDateString);
            return null;
        }
    }

    /**
     * Custom mapping for description field, name convention is start with map then the field name
     * @param source
     * @return
     */
    @Named("mapDescription")
    String mapDescription(MDMetadataType source) {
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);

        if(!items.isEmpty()) {
            // Need to assert only 1 block contains our target
            for(MDDataIdentificationType i : items) {
                // TODO: Null or empty check
                return i.getAbstract().getCharacterString().getValue().toString();
            }
        }
        return "";
    }

    @Named("mapSummaries.temporal")
    List<Map<String,String>> mapSummariesTemporal(MDMetadataType source) {
        List<Map<String,String>> result = new ArrayList<>();
        List<String[]> temp = createExtentTemporal(source);

        for(String[] t : temp) {
            Map<String,String> m = new HashMap<>();
            m.put("start", t[0]);
            m.put("end", t[1]);

            result.add(m);
        }

        return result;
    }

    @Named("mapSummaries.geometry")
    Map mapSummariesGeometry(MDMetadataType source) {
        return createGeometryItems(
                source,
                GeometryUtils::createGeometryFromFromEXBoundingPolygonType,
                GeometryUtils::createGeometryFromEXGeographicBoundingBoxType
        );
    }

    @Named("mapSummaries.status")
    String createSummariesStatus(MDMetadataType source) {
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if (!items.isEmpty()) {
            List<String> temp = new ArrayList<>();
            for (MDDataIdentificationType i : items) {
                // status
                // mdb:identificationInfo/mri:MD_DataIdentification/mri:status/mcc:MD_ProgressCode/@codeListValue
                for (MDProgressCodePropertyType s : i.getStatus()) {
                    temp.add(s.getMDProgressCode().getCodeListValue());
                }
            }
            return String.join(" | ", temp);
        }
        logger.warn("Unable to find status metadata record: " + this.mapUUID(source));
        return null;
    }

    @Named("mapSummaries.scope")
    Map<String, String> createSummariesScope(MDMetadataType source) {
        List<MDMetadataScopeType> items = findMDMetadataScopePropertyType(source);
        if (!items.isEmpty()) {
            for (MDMetadataScopeType i : items) {

                Map<String, String> result = new HashMap<>();
                CodeListValueType codeListValueType = i.getResourceScope().getMDScopeCode();
                result.put("code", codeListValueType != null ? codeListValueType.getCodeListValue() : "");
                CharacterStringPropertyType nameString = i.getName();
                result.put("name", nameString != null ? nameString.getCharacterString().getValue().toString() : "");

                return result;
            }
        }

        logger.warn("Unable to find scope metadata record: " + this.mapUUID(source));
        return null;
    }
    /**
     * Custom mapping for title field, name convention is start with map then the field name
     * @param source
     * @return
     */
    @Named("mapTitle")
    String mapTitle(MDMetadataType source) {
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if(!items.isEmpty()) {
            // Need to assert only 1 block contains our target
            for(MDDataIdentificationType i : items) {
                // TODO: Null or empty check
                AbstractCitationType ac = i.getCitation().getAbstractCitation().getValue();
                if(ac instanceof CICitationType2 type2) {
                    return type2.getTitle().getCharacterString().getValue().toString();
                }
                else if(ac instanceof CICitationType type1) {
                    // Backward compatible
                    return type1.getTitle().getCharacterString().getValue().toString();
                }
            }
        }
        return "";
    }
    /**
     * TODO: Very simple logic here, a dataset is flag as real-time if title contains the word
     *
     * @param source
     * @return
     */
    @Named("mapSummaries.updateFrequency")
    String mapUpdateFrequency(MDMetadataType source) {
        String t = mapTitle(source);
        return t.toLowerCase().contains(REAL_TIME) ? REAL_TIME : null;
    }
    /**
     * TODO: Very simple logic here, if provider name contains IMOS
     *
     * @param source
     * @return
     */
    @Named("mapSummaries.datasetProvider")
    String mapDatasetOwner(MDMetadataType source) {
        List<au.org.aodn.esindexer.model.ProviderModel> providers = mapProviders(source);
        return providers.stream().anyMatch(p -> p.getName().contains("IMOS")) ? "IMOS" : null;
    }

    protected List<Map<String, String>> mapThemesConcepts(MDKeywordsPropertyType descriptiveKeyword) {
        List<Map<String, String>> keywords = new ArrayList<>();
        descriptiveKeyword.getMDKeywords().getKeyword().forEach(keyword -> {
            if (keyword != null) {
                if (keyword.getCharacterString().getValue() instanceof AnchorType value) {
                    keywords.add(Map.of("id", value.getValue(),
                            "url", value.getHref()));
                } else {
                    keywords.add(Map.of("id", keyword.getCharacterString().getValue().toString()));
                }
            }
        });
        return keywords;
    }

    protected String mapThemesTitle(MDKeywordsPropertyType descriptiveKeyword, String uuid) {
        AbstractCitationPropertyType abstractCitationPropertyType = descriptiveKeyword.getMDKeywords().getThesaurusName();
        if (abstractCitationPropertyType != null) {
            CICitationType2 thesaurusNameType2 = (CICitationType2) abstractCitationPropertyType.getAbstractCitation().getValue();
            CharacterStringPropertyType titleString = thesaurusNameType2.getTitle();
            if (titleString != null && titleString.getCharacterString().getValue() instanceof  AnchorType value) {
                if (value.getValue() != null) {
                    return value.getValue();
                } else {
                    return "";
                }
            } else if (titleString != null && titleString.getCharacterString().getValue() instanceof String value) {
                return value;
            }
        }
        logger.debug("Unable to find themes' title for metadata record: " + uuid);
        return "";
    }

    protected String mapThemesDescription(MDKeywordsPropertyType descriptiveKeyword, String uuid) {
        AbstractCitationPropertyType abstractCitationPropertyType = descriptiveKeyword.getMDKeywords().getThesaurusName();
        if (abstractCitationPropertyType != null) {
            CICitationType2 thesaurusNameType2 = (CICitationType2) abstractCitationPropertyType.getAbstractCitation().getValue();
            CharacterStringPropertyType titleString = thesaurusNameType2.getTitle();
            if (titleString != null && titleString.getCharacterString().getValue() instanceof  AnchorType value) {
                if (value.getTitleAttribute() != null) {
                    return value.getTitleAttribute();
                } else {
                    return "";
                }
            } else if (titleString != null && titleString.getCharacterString().getValue() instanceof String value) {
                return thesaurusNameType2.getAlternateTitle().stream().map(CharacterStringPropertyType::getCharacterString).map(JAXBElement::getValue).map(Object::toString).collect(Collectors.joining(", "));
            }
        }
        logger.debug("Unable to find themes' description for metadata record: " + uuid);
        return "";
    }

    protected String mapThemesScheme(MDKeywordsPropertyType descriptiveKeyword, String uuid) {
        AbstractCitationPropertyType abstractCitationPropertyType = descriptiveKeyword.getMDKeywords().getThesaurusName();
        if (abstractCitationPropertyType != null) {
            if (descriptiveKeyword.getMDKeywords().getType() != null) {
                return descriptiveKeyword.getMDKeywords().getType().getMDKeywordTypeCode().getCodeListValue();
            } else {
                return "";
            }
        }
        logger.debug("Unable to find themes' scheme for metadata record: " + uuid);
        return "";
    }

    @Named("mapThemes")
    List<ThemesModel> mapThemes(MDMetadataType source) {
        List<ThemesModel> results = new ArrayList<>();
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if (!items.isEmpty()) {
            for (MDDataIdentificationType i : items) {
                i.getDescriptiveKeywords().forEach(descriptiveKeyword -> {
                    ThemesModel themesModel = ThemesModel.builder().build();
                    String uuid = this.mapUUID(source);

                    themesModel.setConcepts(mapThemesConcepts(descriptiveKeyword));

                    themesModel.setTitle(mapThemesTitle(descriptiveKeyword, uuid));
                    themesModel.setDescription(mapThemesDescription(descriptiveKeyword, uuid));
                    themesModel.setScheme(mapThemesScheme(descriptiveKeyword, uuid));
                    results.add(themesModel);
                });
            }
        }
        return results;
    }

    @Named("mapLinks")
    List<LinkModel> mapLinks(MDMetadataType source) {
        List<LinkModel> results = new ArrayList<>();
        List<MDDistributionType> items = findMDDistributionType(source);
        if (!items.isEmpty()) {
            for (MDDistributionType i : items) {
                i.getTransferOptions().forEach(transferOption -> transferOption.getMDDigitalTransferOptions().getOnLine().forEach(link -> {
                    if (link.getAbstractOnlineResource().getValue() instanceof CIOnlineResourceType2 ciOnlineResource) {
                        LinkModel linkModel = LinkModel.builder().build();
                        if (!ciOnlineResource.getLinkage().getCharacterString().getValue().toString().isEmpty()) {
                            linkModel.setType(Objects.equals(ciOnlineResource.getProtocol().getCharacterString().getValue().toString(), "WWW:LINK-1.0-http--link") ? "text/html" : "");
                            linkModel.setHref(ciOnlineResource.getLinkage().getCharacterString().getValue().toString());
                            linkModel.setRel(AppConstants.RECOMMENDED_LINK_REL_TYPE);
                            linkModel.setTitle(ciOnlineResource.getName() != null ? ciOnlineResource.getName().getCharacterString().getValue().toString() : null);
                            results.add(linkModel);
                        }
                    }
                }));
            }
        }
        return results;
    }

    // TODO: need to handle exception
    @Named("mapProviders")
    List<ProviderModel> mapProviders(MDMetadataType source) {
        List<ProviderModel> results = new ArrayList<>();
        source.getContact().forEach(item -> {
            if (item.getAbstractResponsibility().getValue() instanceof CIResponsibilityType2 ciResponsibility) {
                ciResponsibility.getParty().forEach(party -> {
                    try {
                        ProviderModel providerModel = ProviderModel.builder().build();
                        providerModel.setRoles(Collections.singletonList(ciResponsibility.getRole().getCIRoleCode().getCodeListValue()));
                        CIOrganisationType2 organisationType2 = (CIOrganisationType2) party.getAbstractCIParty().getValue();
                        providerModel.setName(organisationType2.getName().getCharacterString().getValue().toString());
                        organisationType2.getIndividual().forEach(individual -> individual.getCIIndividual().getContactInfo().forEach(contactInfo -> {
                            contactInfo.getCIContact().getOnlineResource().forEach(onlineResource -> {
                                providerModel.setUrl(onlineResource.getCIOnlineResource().getLinkage().getCharacterString().getValue().toString());
                            });
                        }));
                        results.add(providerModel);
                    } catch (ClassCastException e) {
                        logger.error("Unable to cast getAbstractCIParty().getValue() to CIOrganisationType2 for metadata record: " + this.mapUUID(source));
                    }
                });
            }
        });
        return results;
    }

    @Named("mapLicense")
    String mapLicense(MDMetadataType source) {
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        List<String> potentialKeys = Arrays.asList("license", "creative commons");
        List<String> licenses = new ArrayList<>();
        if (!items.isEmpty()) {
            for (MDDataIdentificationType i : items) {
                i.getResourceConstraints().forEach(resourceConstraint -> {
                    if (resourceConstraint.getAbstractConstraints().getValue() instanceof MDLegalConstraintsType legalConstraintsType) {
                        legalConstraintsType.getOtherConstraints().forEach(otherConstraints -> {
                            for (String potentialKey : potentialKeys) {
                                if (otherConstraints.getCharacterString() != null && otherConstraints.getCharacterString().getValue().toString().toLowerCase().contains(potentialKey)) {
                                    licenses.add(otherConstraints.getCharacterString().getValue().toString());
                                }
                            }
                        });
                        // try finding in different location if above didn't add any values to licenses array
                        if (licenses.isEmpty()) {
                            if (!legalConstraintsType.getReference().isEmpty() || legalConstraintsType.getReference() != null) {
                                legalConstraintsType.getReference().forEach(reference -> {
                                    if (reference.getAbstractCitation().getValue() instanceof CICitationType2 ciCitationType2) {
                                        if (ciCitationType2.getTitle() != null) {
                                            licenses.add(ciCitationType2.getTitle().getCharacterString().getValue().toString());
                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            }
        }
        if (!licenses.isEmpty()) {
            return String.join(" | ", licenses);
        } else {
            logger.debug("Unable to find license information for metadata record: " + this.mapUUID(source));
            return "";
        }
    }

    @Named("mapContacts")
    List<ContactsModel> mapContacts(MDMetadataType source) {
        List<ContactsModel> results = new ArrayList<>();
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if (!items.isEmpty()) {
            for (MDDataIdentificationType item : items) {
                item.getPointOfContact().forEach(poc -> {
                    AbstractResponsibilityType responsibilityType = poc.getAbstractResponsibility().getValue();
                    if (responsibilityType instanceof CIResponsibilityType2 ciResponsibility) {
                        ContactsModel contactsModel = ContactsModel.builder().build();
                        contactsModel.setRoles(mapContactsRole(ciResponsibility));

                        if (ciResponsibility.getParty().isEmpty()) {
                            logger.warn("Unable to find contact info for metadata record: " + this.mapUUID(source));
                        } else {
                            ciResponsibility.getParty().forEach(party -> {
                                contactsModel.setOrganization(mapContactsOrganization(party));
                                try {

                                    AtomicReference<String> name = new AtomicReference<>("");
                                    AtomicReference<String> position = new AtomicReference<>("");
                                    List<Map<String, Object>> addresses = new ArrayList<>();
                                    List<String> emailAddresses = new ArrayList<>();
                                    List<Map<String, String>> phones = new ArrayList<>();
                                    List<Map<String, String>> onlineResources = new ArrayList<>();


                                    CIOrganisationType2 organisation = (CIOrganisationType2) party.getAbstractCIParty().getValue();
                                    AtomicReference<List<CIContactPropertyType2>> contactInfoList = new AtomicReference<>();

                                    if (organisation.getIndividual().isEmpty()) {
                                        contactInfoList.set(organisation.getContactInfo());
                                    } else {
                                        organisation.getIndividual().forEach(individual -> {
                                            name.set(mapContactsName(individual));
                                            position.set(mapContactsPosition(individual));
                                            contactInfoList.set(individual.getCIIndividual().getContactInfo());
                                        });
                                    }

                                    contactInfoList.get().forEach(contactInfo -> contactInfo.getCIContact().getAddress().forEach(address -> {
                                        // addresses
                                        addresses.add(mapContactsAddress(address));
                                        // emails
                                        address.getCIAddress().getElectronicMailAddress().forEach(electronicMailAddress -> {
                                            emailAddresses.add(mapContactsEmail(electronicMailAddress));
                                        });
                                        // phones
                                        contactInfo.getCIContact().getPhone().forEach(phone -> {
                                            phones.add(mapContactsPhone(phone));
                                        });
                                        // online resources
                                        contactInfo.getCIContact().getOnlineResource().forEach(onlineResource -> {
                                            onlineResources.add(mapContactsOnlineResource(onlineResource));
                                        });
                                    }));

                                    contactsModel.setName(name.get());
                                    contactsModel.setPosition(position.get());
                                    contactsModel.setAddresses(addresses);
                                    contactsModel.setEmails(emailAddresses);
                                    contactsModel.setPhones(phones);
                                    contactsModel.setLinks(onlineResources);

                                } catch (Exception e) {
                                    logger.warn("Unable to find contact info for metadata record: " + this.mapUUID(source));
                                }
                            });
                            results.add(contactsModel);
                        }
                    }
                });
            }
        }
        return results;
    }

    protected String mapContactsRole(CIResponsibilityType2 ciResponsibility) {
        CodeListValueType roleCode = ciResponsibility.getRole().getCIRoleCode();
        if (roleCode != null) { return roleCode.getCodeListValue(); } else { return ""; }
    }

    protected String mapContactsOrganization(AbstractCIPartyPropertyType2 party) {
        String organisationString = party.getAbstractCIParty().getValue().getName().getCharacterString().getValue().toString();
        if (organisationString != null) { return organisationString; } else { return ""; }

    }

    protected String mapContactsName(CIIndividualPropertyType2 individual) {
        CharacterStringPropertyType nameString = individual.getCIIndividual().getName();
        if (nameString != null) { return individual.getCIIndividual().getName().getCharacterString().getValue().toString(); } else { return ""; }
    }

    protected String mapContactsPosition(CIIndividualPropertyType2 individual) {
        CharacterStringPropertyType positionString = individual.getCIIndividual().getPositionName();
        if (positionString != null) { return individual.getCIIndividual().getPositionName().getCharacterString().getValue().toString(); } else { return ""; }
    }

    protected Map<String, Object> mapContactsAddress(CIAddressPropertyType2 address) {
        Map<String, Object> addressItem = new HashMap<>();
        List<String> deliveryPoints = new ArrayList<>();

        address.getCIAddress().getDeliveryPoint().forEach(deliveryPoint -> {
            String deliveryPointString = deliveryPoint.getCharacterString().getValue().toString();
            deliveryPoints.add(deliveryPointString != null ? deliveryPointString : "");
        });
        addressItem.put("deliveryPoint", deliveryPoints);

        CharacterStringPropertyType cityString = address.getCIAddress().getCity();
        addressItem.put("city", cityString != null ? cityString.getCharacterString().getValue().toString() : "");

        CharacterStringPropertyType administrativeAreaString = address.getCIAddress().getAdministrativeArea();
        addressItem.put("administrativeArea", administrativeAreaString != null ? administrativeAreaString.getCharacterString().getValue().toString() : "");

        CharacterStringPropertyType postalCodeString = address.getCIAddress().getPostalCode();
        addressItem.put("postalCode", postalCodeString != null ? postalCodeString.getCharacterString().getValue().toString() : "");

        CharacterStringPropertyType countryString = address.getCIAddress().getCountry();
        addressItem.put("country", countryString != null ? countryString.getCharacterString().getValue().toString() : "");

        return addressItem;
    }

    protected String mapContactsEmail(CharacterStringPropertyType electronicMailAddress) {
        if (electronicMailAddress != null) {
            return electronicMailAddress.getCharacterString().getValue().toString();
        } else {
            return "";
        }
    }

    protected Map<String, String> mapContactsPhone(CITelephonePropertyType2 phone) {
        Map<String, String> phoneItem = new HashMap<>();

        CharacterStringPropertyType phoneString = phone.getCITelephone().getNumber();
        phoneItem.put("value", phoneString != null ? phoneString.getCharacterString().getValue().toString() : "");

        CodeListValueType phoneCode = phone.getCITelephone().getNumberType().getCITelephoneTypeCode();
        phoneItem.put("roles", phoneCode != null ? phoneCode.getCodeListValue() : "");

        return phoneItem;
    }

    protected Map<String, String> mapContactsOnlineResource(CIOnlineResourcePropertyType2 onlineResource) {
        Map<String, String> onlineResourceItem = new HashMap<>();

        CharacterStringPropertyType linkString = onlineResource.getCIOnlineResource().getLinkage();
        onlineResourceItem.put("href", linkString != null ? linkString.getCharacterString().getValue().toString() : "");

        CharacterStringPropertyType resourceNameString = onlineResource.getCIOnlineResource().getName();
        onlineResourceItem.put("title", resourceNameString != null ? resourceNameString.getCharacterString().getValue().toString() : "");

        CharacterStringPropertyType linkTypeString = onlineResource.getCIOnlineResource().getProtocol();
        onlineResourceItem.put("type", linkTypeString != null ? linkTypeString.getCharacterString().getValue().toString() : "");

        return onlineResourceItem;
    }

    @Named("mapLanguages")
    protected List<LanguageModel> mapLanguages(MDMetadataType source) {
        List<LanguageModel> results = new ArrayList<>();
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if (!items.isEmpty()) {
            for (MDDataIdentificationType i : items) {

                LanguageModel languageModel = LanguageModel.builder().build();

                String langCode = mapLanguagesCode(i) != null ? mapLanguagesCode(i) : "";
                languageModel.setCode(langCode);

                // all metadata records are in English anyway
                switch (langCode) {
                    case "eng" -> languageModel.setName("English");
                    case "fra" -> languageModel.setName("French");
                    default -> {
                        logger.warn("Making assumption...unable to find language name for metadata record: " + this.mapUUID(source));
                        languageModel.setCode("eng");
                        languageModel.setName("English");
                    }
                }

                results.add(languageModel);
            }
        }
        return results;
    }

    protected String mapLanguagesCode(MDDataIdentificationType i) {
        try {
            return i.getDefaultLocale().getPTLocale().getValue().getLanguage().getLanguageCode().getCodeListValue();
        } catch (NullPointerException e) {
            return null;
        }
    }


    protected <R> R createGeometryItems(
            MDMetadataType source,
            Function<List<Object>, R> exBoundingPolygonTypeHandler,
            Function<List<Object>, R> exGeographicBoundingBoxTypeHandler) {

        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if(!items.isEmpty()) {
            // Need to assert only 1 block contains our target
            for(MDDataIdentificationType i : items) {
                // We only concern geographicElement here
                List<EXExtentType> ext = i.getExtent()
                        .stream()
                        .filter(f -> f.getAbstractExtent() != null)
                        .filter(f -> f.getAbstractExtent().getValue() != null)
                        .filter(f -> f.getAbstractExtent().getValue() instanceof EXExtentType)
                        .map(f -> (EXExtentType)f.getAbstractExtent().getValue())
                        .filter(f -> f.getGeographicElement() != null)
                        .toList();

                for(EXExtentType e : ext) {
                    try {
                        // TODO: pay attention here
                        List<Object> rawInput = e.getGeographicElement()
                                .stream()
                                .map(AbstractEXGeographicExtentPropertyType::getAbstractEXGeographicExtent)
                                .filter(m -> m.getValue() instanceof EXBoundingPolygonType || m.getValue() instanceof EXGeographicBoundingBoxType)
                                .map(m -> {
                                    if (m.getValue() instanceof EXBoundingPolygonType exBoundingPolygonType) {
                                        if (!exBoundingPolygonType.getPolygon().isEmpty() && exBoundingPolygonType.getPolygon().get(0).getAbstractGeometry() != null) {
                                            return exBoundingPolygonType;
                                        }
                                    } else if (m.getValue() instanceof EXGeographicBoundingBoxType) {
                                        return (EXGeographicBoundingBoxType) m.getValue();
                                    }
                                    return null; // Handle other cases or return appropriate default value
                                })
                                .filter(Objects::nonNull) // Filter out null values if any
                                .collect(Collectors.toList());

                        if (!rawInput.isEmpty() && rawInput.get(0) instanceof EXBoundingPolygonType) {
                            return exBoundingPolygonTypeHandler.apply(rawInput);
                        }
                        else if (!rawInput.isEmpty() && rawInput.get(0) instanceof EXGeographicBoundingBoxType) {
                            return exGeographicBoundingBoxTypeHandler.apply(rawInput);
                        }
                    }
                    catch (MappingValueException ex) {
                        logger.warn(ex.getMessage() + " for metadata record: " + this.mapUUID(source));
                    }
                }
            }
        }
        return null;
    }

    protected List<MDDataIdentificationType> findMDDataIdentificationType(MDMetadataType source) {
        // Read the raw XML to understand the structure.
        return source.getIdentificationInfo()
                .stream()
                .filter(f -> f.getAbstractResourceDescription() != null)
                .filter(f -> f.getAbstractResourceDescription().getValue() != null)
                .filter(f -> f.getAbstractResourceDescription().getValue() instanceof MDDataIdentificationType)
                .map(f -> (MDDataIdentificationType)f.getAbstractResourceDescription().getValue())
                .collect(Collectors.toList());
    }

    protected List<MDMetadataScopeType> findMDMetadataScopePropertyType(MDMetadataType source) {
        return source.getMetadataScope()
                .stream()
                .map(MDMetadataScopePropertyType::getMDMetadataScope)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    protected List<MDDistributionType> findMDDistributionType(MDMetadataType source) {
        return source.getDistributionInfo()
                .stream()
                .filter(f -> f.getAbstractDistribution() != null)
                .filter(f -> f.getAbstractDistribution().getValue() != null)
                .filter(f -> f.getAbstractDistribution().getValue() instanceof MDDistributionType)
                .map(f -> (MDDistributionType)f.getAbstractDistribution().getValue())
                .collect(Collectors.toList());
    }
}
