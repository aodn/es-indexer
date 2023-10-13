package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.MappingValueException;
import au.org.aodn.esindexer.model.SummariesModel;
import au.org.aodn.esindexer.utils.BBoxUtils;
import au.org.aodn.esindexer.model.StacCollectionModel;
import au.org.aodn.esindexer.utils.GeometryUtils;
import au.org.aodn.metadata.iso19115_3_2018.*;
import org.json.JSONObject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Mapper(componentModel = "spring")
public abstract class StacCollectionMapperServiceImpl implements StacCollectionMapperService {

    @Mapping(target="uuid", source = "source", qualifiedByName = "mapUUID")
    @Mapping(target="title", source = "source", qualifiedByName = "mapTitle" )
    @Mapping(target="description", source = "source", qualifiedByName = "mapDescription")
    @Mapping(target="summaries.score", source = "source", qualifiedByName = "mapSummaries.score")
    @Mapping(target="summaries.status", source = "source", qualifiedByName = "mapSummaries.status")
    @Mapping(target="summaries.creation", source = "source", qualifiedByName = "mapSummaries.creation")
    @Mapping(target="summaries.geometry", source = "source", qualifiedByName = "mapSummaries.geometry")
    @Mapping(target="extent.bbox", source = "source", qualifiedByName = "mapExtentBbox")
    @Mapping(target="contacts", source = "source", qualifiedByName = "mapContacts")
    @Mapping(target="themes", source = "source", qualifiedByName = "mapThemes")
    public abstract StacCollectionModel mapToSTACCollection(MDMetadataType source);

    private static final Logger logger = LoggerFactory.getLogger(StacCollectionMapperServiceImpl.class);

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

    @Named("mapSummaries.geometry")
    Map mapSummariesGeometry(MDMetadataType source) {
        return createGeometryItems(
                source,
                GeometryUtils::createGeometryFromFromEXBoundingPolygonType,
                GeometryUtils::createGeometryFromEXGeographicBoundingBoxType
        );
    }

    @Named("mapSummaries.score")
    Integer createSummariesScore(MDMetadataType source) {
        //TODO: need cal logic
        return 0;
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

    @Named("mapSummaries.creation")
    List<ZonedDateTime> createSummariesCreation(MDMetadataType source) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);

        if (!items.isEmpty()) {
            for (MDDataIdentificationType i : items) {
                List<ZonedDateTime> temp = new ArrayList<>();
                // mdb:dateInfo/cit:CI_Date/cit:date/gco:DateTime/#text
                // TODO: these backward compatible code make the code ugly, need to refactor
                try {
                    AbstractCitationType ac = i.getCitation().getAbstractCitation().getValue();
                    if(ac instanceof CICitationType2 type2) {
                        type2.getDate().forEach(f -> {
                            if (f.getCIDate().getDateType().getCIDateTypeCode().getCodeListValue().equals("creation")) {
                                temp.add(f.getCIDate().getDate().getDateTime().toGregorianCalendar().toZonedDateTime());
                            }
                        });
                    }
                    else if(ac instanceof CICitationType type1) {
                        // Backward compatible
                        type1.getDate().forEach(f -> {
                            if (f.getCIDate().getDateType().getCIDateTypeCode().getCodeListValue().equals("creation")) {
                                temp.add(f.getCIDate().getDate().getDateTime().toGregorianCalendar().toZonedDateTime());
                            }
                        });
                    }
                } catch (NullPointerException e) {
                    // Do nothing
                    logger.warn("Unable to find creation date for metadata record: " + this.mapUUID(source));
                }
                finally {
                    return temp;
                }
            }
        }
        logger.warn("Unable to find creation metadata record: " + this.mapUUID(source));
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
                    type1.getTitle().getCharacterString().getValue().toString();
                }
            }
        }
        return "";
    }

    @Named("mapThemes")
    List<Object> mapThemes(MDMetadataType source) {
        List<Object> themes = new ArrayList<>();
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if (!items.isEmpty()) {
            for (MDDataIdentificationType i : items) {
                i.getDescriptiveKeywords().forEach(descriptiveKeyword -> {
                    List<Map<String, Object>> keywords = new ArrayList<>();
                    descriptiveKeyword.getMDKeywords().getKeyword().forEach(keyword -> {
                        if (keyword.getCharacterString().getValue() instanceof AnchorType value) {
                            keywords.add(Map.of("id", value.getValue(),
                                    "url", value.getHref()));
                        } else {
                            keywords.add(Map.of("id", keyword.getCharacterString().getValue().toString()));
                        }
                    });

//                    if (descriptiveKeyword.getMDKeywords().getThesaurusName().getAbstractCitation().getValue() instanceof CICitationType2 value) {
//                        themes.add(Map.of(
//                                "scheme", descriptiveKeyword.getMDKeywords().getType().getMDKeywordTypeCode().getCodeListValue(),
//                                "concepts", keywords,
//                                "title", value.getTitle().getCharacterString().getValue().toString()
//                        ));
//                    }

                    themes.add(Map.of(
                            "scheme", descriptiveKeyword.getMDKeywords().getType().getMDKeywordTypeCode().getCodeListValue(),
                            "concepts", keywords)
                    );
                });
            }
        }
        return themes;
    }

    @Named("mapContacts")
    List<Map<String, Object>> mapContact(MDMetadataType source) {
        List<Map<String, Object>> contacts = new ArrayList<>();
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if (!items.isEmpty()) {
            for (MDDataIdentificationType item : items) {
                item.getPointOfContact().forEach(poc -> {
                    AbstractResponsibilityType responsibilityType = poc.getAbstractResponsibility().getValue();
                    if (responsibilityType instanceof CIResponsibilityType2 ciResponsibility) {
                        Map<String, Object> contactItem = new HashMap<>();

                        CodeListValueType roleCode = ciResponsibility.getRole().getCIRoleCode();
                        contactItem.put("roles", roleCode != null ? roleCode.getCodeListValue() : "");

                        ciResponsibility.getParty().forEach(party -> {
                            String organisationString = party.getAbstractCIParty().getValue().getName().getCharacterString().getValue().toString();
                            contactItem.put("organization", organisationString != null ? organisationString : "");

                            try {
                                ((CIOrganisationType2) party.getAbstractCIParty().getValue()).getIndividual().forEach(individual -> {

                                    CharacterStringPropertyType nameString = individual.getCIIndividual().getName();
                                    contactItem.put("name", nameString != null ? individual.getCIIndividual().getName().getCharacterString().getValue().toString() : "");

                                    CharacterStringPropertyType positionString = individual.getCIIndividual().getPositionName();
                                    contactItem.put("position", positionString != null ? individual.getCIIndividual().getPositionName().getCharacterString().getValue().toString(): "");

                                    individual.getCIIndividual().getContactInfo().forEach(contactInfo -> {
                                        List<Map<String, Object>> addresses = new ArrayList<>();
                                        List<String> emailAddresses = new ArrayList<>();
                                        contactInfo.getCIContact().getAddress().forEach(address -> {
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


                                            address.getCIAddress().getElectronicMailAddress().forEach(electronicMailAddress -> {
                                                emailAddresses.add(electronicMailAddress != null ? electronicMailAddress.getCharacterString().getValue().toString() : "");
                                            });


                                            addresses.add(addressItem);
                                        });

                                        contactItem.put("addresses", addresses);
                                        contactItem.put("emails", emailAddresses);

                                        // ** PHONE
                                        // list of phone AND code type
                                        List<Map<String, String>> phones = new ArrayList<>();
                                        contactInfo.getCIContact().getPhone().forEach(phone -> {
                                            Map<String, String> phoneItem = new HashMap<>();

                                            CharacterStringPropertyType phoneString = phone.getCITelephone().getNumber();
                                            phoneItem.put("value", phoneString != null ? phoneString.getCharacterString().getValue().toString() : "");

                                            CodeListValueType phoneCode = phone.getCITelephone().getNumberType().getCITelephoneTypeCode();
                                            phoneItem.put("roles", phoneCode != null ? phoneCode.getCodeListValue() : "");

                                            phones.add(phoneItem);
                                        });
                                        contactItem.put("phones", phones);

                                        // ** ONLINE RESOURCES
                                        // list of resources including link and name
                                        List<Map<String, String>> onlineResources = new ArrayList<>();
                                        contactInfo.getCIContact().getOnlineResource().forEach(onlineResource -> {
                                            Map<String, String> onlineResourceItem = new HashMap<>();

                                            CharacterStringPropertyType linkString = onlineResource.getCIOnlineResource().getLinkage();
                                            onlineResourceItem.put("href", linkString != null ? linkString.getCharacterString().getValue().toString() : "");

                                            CharacterStringPropertyType resourceNameString = onlineResource.getCIOnlineResource().getName();
                                            onlineResourceItem.put("title", resourceNameString != null ? resourceNameString.getCharacterString().getValue().toString() : "");

                                            CharacterStringPropertyType linkTypeString = onlineResource.getCIOnlineResource().getProtocol();
                                            onlineResourceItem.put("type", linkTypeString != null ? linkTypeString.getCharacterString().getValue().toString() : "");

                                            onlineResources.add(onlineResourceItem);
                                        });
                                        contactItem.put("links", onlineResources);
                                    });
                                });
                            } catch (Exception e) {
                                logger.warn("Unable to find contact info for metadata record: " + this.mapUUID(source));
                            }

                        });
                        contacts.add(contactItem);
                    }
                });
            }
        }
        return contacts;
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
                                    if (m.getValue() instanceof EXBoundingPolygonType) {
                                        return (EXBoundingPolygonType) m.getValue();
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
}
