package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.model.GeoNetworkField;
import au.org.aodn.esindexer.model.MediaType;
import au.org.aodn.esindexer.model.RelationType;
import au.org.aodn.esindexer.utils.*;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.stac.model.*;

import au.org.aodn.metadata.iso19115_3_2018.*;
import au.org.aodn.stac.util.JsonUtil;
import jakarta.xml.bind.JAXBElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static au.org.aodn.esindexer.model.GeoNetworkField.*;
import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

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
    @Mapping(target="summaries.credits", source = "source", qualifiedByName = "mapSummaries.credits")
    @Mapping(target="summaries.geometry", source = "source", qualifiedByName = "mapSummaries.geometry")
    @Mapping(target="summaries.temporal", source = "source", qualifiedByName = "mapSummaries.temporal")
    @Mapping(target="summaries.updateFrequency", source = "source", qualifiedByName = "mapSummaries.updateFrequency")
    @Mapping(target="summaries.datasetProvider", source = "source", qualifiedByName = "mapSummaries.datasetProvider")
    @Mapping(target="summaries.datasetGroup", source = "source", qualifiedByName = "mapSummaries.datasetGroup")
    @Mapping(target="extent.bbox", source = "source", qualifiedByName = "mapExtentBbox")
    @Mapping(target="extent.temporal", source = "source", qualifiedByName = "mapExtentTemporal")
    @Mapping(target="contacts", source = "source", qualifiedByName = "mapContacts")
    @Mapping(target="themes", source = "source", qualifiedByName = "mapThemes")
    @Mapping(target="languages", source = "source", qualifiedByName = "mapLanguages")
    @Mapping(target="links", source = "source", qualifiedByName = "mapLinks")
    @Mapping(target="license", source = "source", qualifiedByName = "mapLicense")
    @Mapping(target="providers", source = "source", qualifiedByName = "mapProviders")
    @Mapping(target="citation", source="source", qualifiedByName = "mapCitation")
    @Mapping(target="summaries.statement", source="source", qualifiedByName = "mapSummaries.statement")
    @Mapping(target="summaries.creation", source = "source", qualifiedByName = "mapSummaries.creation")
    @Mapping(target="summaries.revision", source = "source", qualifiedByName = "mapSummaries.revision")
    public abstract StacCollectionModel mapToSTACCollection(MDMetadataType source);


    private static final Logger logger = LogManager.getLogger(StacCollectionMapperService.class);

    @Value("${spring.jpa.properties.hibernate.jdbc.time_zone}")
    private String timeZoneId;

    @Autowired
    private GeoNetworkService geoNetworkService;

    @Named("mapUUID")
    String mapUUID(MDMetadataType source) {
        return source.getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue().toString();
    }
    /**
     * According to the spec, the bbox must be an of length 2*n where n is number of dimension, so a 2D map, the
     * dimension is 4 and therefore it must be a box.
     *
     * @param source
     * @return The list<BigDecimal> must be of size 4 due to 2D map.
     */
    @Named("mapExtentBbox")
    List<List<BigDecimal>> mapExtentBbox(MDMetadataType source) {
        return createGeometryItems(
                source,
                BBoxUtils::createBBoxFrom
        );
    }

    @Named("mapExtentTemporal")
    List<String[]> mapExtentTemporal(MDMetadataType source) {
        return TemporalUtils.concatOverallTemporalRange(createExtentTemporal(source));
    }

    List<String[]> createExtentTemporal(MDMetadataType source) {

        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
        if (items.isEmpty()) {
            logger.warn("Unable to find extent temporal information for metadata record: " + this.mapUUID(source));
            return null;
        }

        List<String[]> result = new ArrayList<>();
        for (MDDataIdentificationType item : items) {
            item.getExtent().forEach(extent -> {
                if (!(extent.getAbstractExtent().getValue() instanceof EXExtentType exExtentType)) {
                    return;
                }

                exExtentType.getTemporalElement().forEach(temporalElement -> {
                    String[] temporalPair = new String[2];
                    temporalPair[0] = null;
                    temporalPair[1] = null;
                    var abstractTimePrimitive = safeGet(() ->
                            temporalElement.getEXTemporalExtent().getValue().getExtent().getAbstractTimePrimitive().getValue())
                            .orElse(null);
                    if (abstractTimePrimitive instanceof TimePeriodType timePeriodType) {

                        var pair0 = safeGet(() -> timePeriodType.getBegin().getTimeInstant().getTimePosition().getValue().get(0));
                        if (pair0.isEmpty()) {
                            pair0 = safeGet(() -> timePeriodType.getBeginPosition().getValue().get(0));
                        }
                        pair0.ifPresent(pair -> temporalPair[0] = convertDateToZonedDateTime(pair));

                        var pair1 = safeGet(() -> timePeriodType.getEnd().getTimeInstant().getTimePosition().getValue().get(0));
                        if (pair1.isEmpty()) {
                            pair1 = safeGet(() -> timePeriodType.getEndPosition().getValue().get(0));
                        }
                        pair1.ifPresent(pair -> temporalPair[1] = convertDateToZonedDateTime(pair));
                    }

                    result.add(temporalPair);
                });
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
        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);

        if(!items.isEmpty()) {
            // Need to assert only 1 block contains our target
            for(MDDataIdentificationType i : items) {
                // TODO: Null or empty check
                return i.getAbstract().getCharacterString().getValue().toString();
            }
        }
        return "";
    }



    @Named("mapCitation")
    String mapCitation(MDMetadataType source) {

        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
        var citation = Citation.builder().build();
        if(items.isEmpty()) {
            return JsonUtil.toJsonString(citation);
        }
        for(MDDataIdentificationType item : items) {
            var resourceConstraints = safeGet(item::getResourceConstraints);
            if (resourceConstraints.isEmpty()) {
                continue;
            }
            for (var resourceConstraint : resourceConstraints.get()) {
                var abstractConstraints = safeGet(() -> resourceConstraint.getAbstractConstraints().getValue()).orElse(null);
                if (abstractConstraints == null) {
                    continue;
                }

                if (abstractConstraints instanceof MDLegalConstraintsType legalConstraints) {
                    var otherConstraints = safeGet(legalConstraints::getOtherConstraints).orElse(null);
                    if (otherConstraints == null) {
                        continue;
                    }
                    otherConstraints.forEach(constraint -> safeGet(() -> constraint.getCharacterString().getValue().toString()).ifPresent(cons -> {
                        if (isSuggestedCitation(cons)) {
                            citation.setSuggestedCitation(cons);
                        } else {
                            citation.addOtherConstraint(cons);
                        }
                    }));
                }
                else if (abstractConstraints instanceof MDConstraintsType constraints) {
                    var useLimitations = safeGet(constraints::getUseLimitation);
                    if (useLimitations.isEmpty()) {
                        continue;
                    }
                    useLimitations.get().forEach(limitation -> safeGet(() ->
                            limitation.getCharacterString().getValue().toString()).ifPresent(citation::addUseLimitation));
                }
            }
        }
        return JsonUtil.toJsonString(citation);
    }

    @Named("mapSummaries.statement")
    String mapSummariesStatement(MDMetadataType source) {
        var lineages = MapperUtils.findMDResourceLineage(source);
        if (lineages.isEmpty()) {
            return null;
        }
        for (var lineage : lineages) {
            var abstractLiLineage = lineage.getAbstractLineageInformation().getValue();
            if (!(abstractLiLineage instanceof LILineageType liLineage)) {
                continue;
            }
            var statement = safeGet(() -> liLineage.getStatement().getCharacterString().getValue().toString());
            if (statement.isEmpty()) {
                continue;
            }
            return statement.get();
        }
        return null;
    }


    /**
     * Because suggested citation and other constraints are in the same block,
     * we need to tell whether a constraint is a suggested citation or not.
     * According to previous discussion, if a suggested citation is too strange
     * (not all organizations follow the same format),
     * don't worry about it. Just show it in "other constraint" part.
     * @param constraint the constraint
     * @return true if the constraint is like a suggested citation
     */
    private static boolean isSuggestedCitation(String constraint) {
        String regex = "\\[[^]]+]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(constraint);
        if (constraint.toLowerCase().contains("citation") && matcher.find()) {
            return true;
        }
        if (constraint.toLowerCase().contains("cite as")) {
            return true;
        }
        return false;
    }

    @Named("mapSummaries.temporal")
    List<Map<String,String>> mapSummariesTemporal(MDMetadataType source) {
        List<Map<String,String>> result = new ArrayList<>();
        List<String[]> temp = createExtentTemporal(source);
        Map<String, String> temporal = new HashMap<>();
        if (temp != null) {
            for (String[] t : temp) {
                temporal.put("start", t[0]);
                temporal.put("end", t[1]);
            }
        }

        if (!temporal.isEmpty()) {
            result.add(temporal);
        }
        if (!result.isEmpty()) {
            return result;
        }

        return null;
    }

    @Named("mapSummaries.creation")
    String mapSummariesCreation(MDMetadataType source) {
        var dateSources = MapperUtils.findMDDateInfo(source);
        var dateMap = getMetadataDateInfoFrom(dateSources);
        return safeGet(() -> dateMap.get(creation)).orElse(null);
    }

    @Named("mapSummaries.revision")
    String mapSummariesRevision(MDMetadataType source) {
        var dateSources = MapperUtils.findMDDateInfo(source);
        var dateMap = getMetadataDateInfoFrom(dateSources);
        return safeGet(() -> dateMap.get(revision)).orElse(null);
    }

    private HashMap<GeoNetworkField, String> getMetadataDateInfoFrom(List<AbstractTypedDatePropertyType> dateSources) {
        var dateMap = new HashMap<GeoNetworkField, String>();
        dateSources.forEach(dateSource -> {
            var typeValue = safeGet(() -> dateSource.getAbstractTypedDate().getValue()).orElse(null);
            if (!(typeValue instanceof CIDateType2 ciDateType2) ) {
                return;
            }
            var type = safeGet(() -> ciDateType2.getDateType().getCIDateTypeCode().getCodeListValue());
            var date = safeGet(() -> ciDateType2.getDate().getDateTime());
            if (type.isPresent() && date.isPresent()) {
                dateMap.put(GeoNetworkField.valueOf(type.get()), date.get().toString());
            }
        });
        return dateMap;
    }

    @Named("mapSummaries.geometry")
    Map<?,?> mapSummariesGeometry(MDMetadataType source) {
        return createGeometryItems(
                source,
                GeometryUtils::createGeometryFrom
        );
    }

    @Named("mapSummaries.status")
    String createSummariesStatus(MDMetadataType source) {
        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
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
        List<MDMetadataScopeType> items = MapperUtils.findMDMetadataScopePropertyType(source);
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
        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
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
     * Map the field credits, it is under
     * <mri:MD_DataIdentification>
     *     <mri:credit>XXXXXX</mri:credit>
     *     <mri:credit>YYYYYY</mri:credit>
     * </mri:MD_DataIdentification>
     * @param source
     * @return
     */
    @Named("mapSummaries.credits")
    List<String> mapSummariesCredits(MDMetadataType source) {
        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
        return items
                .stream()
                .map(AbstractMDIdentificationType::getCredit)
                .flatMap(Collection::stream)
                .map(CharacterStringPropertyType::getCharacterString)
                .filter(Objects::nonNull)
                .map(JAXBElement::getValue)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(Objects::nonNull)
                .toList();
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
        List<ProviderModel> providers = mapProviders(source);
        return providers.stream().anyMatch(p -> p.getName().contains("IMOS")) ? "IMOS" : null;
    }

    @Named("mapSummaries.datasetGroup")
    String mapGeoNetworkGroup(MDMetadataType source) {
        try {
            String group = geoNetworkService.findGroupById(mapUUID(source));
            return group != null ? group.toLowerCase() : null;
        }
        catch (IOException e) {
            return null;
        }
    }

    protected List<ConceptModel> mapThemesConcepts(MDKeywordsPropertyType descriptiveKeyword) {
        List<ConceptModel> concepts = new ArrayList<>();
        descriptiveKeyword.getMDKeywords().getKeyword().forEach(keyword -> {
            if (keyword != null) {
                ConceptModel conceptModel = ConceptModel.builder().build();
                if (keyword.getCharacterString().getValue() instanceof AnchorType value) {
                    conceptModel.setId(value.getValue());
                    conceptModel.setUrl(value.getHref());
                } else {
                    conceptModel.setId(keyword.getCharacterString().getValue().toString());
                }
                concepts.add(conceptModel);
            }
        });
        return concepts;
    }

    protected String mapThemesTitle(MDKeywordsPropertyType descriptiveKeyword, String uuid) {
        AbstractCitationPropertyType abstractCitationPropertyType = descriptiveKeyword.getMDKeywords().getThesaurusName();
        if (abstractCitationPropertyType != null) {
            if(abstractCitationPropertyType.getAbstractCitation() != null
                    && abstractCitationPropertyType.getAbstractCitation().getValue() instanceof CICitationType2 thesaurusNameType2) {

                CharacterStringPropertyType titleString = thesaurusNameType2.getTitle();
                if (titleString != null
                        && titleString.getCharacterString() != null
                        && titleString.getCharacterString().getValue() instanceof AnchorType value) {
                    return (value.getValue() != null ? value.getValue() : "");
                } else if (titleString != null
                        && titleString.getCharacterString() != null
                        && titleString.getCharacterString().getValue() instanceof String value) {
                    return value;
                }
            }
        }
        logger.debug("Unable to find themes' title for metadata record: {}", uuid);
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
            }
            else if (titleString != null && titleString.getCharacterString().getValue() instanceof String value) {
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
        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
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
        final List<LinkModel> results = new ArrayList<>();

        List<MDDistributionType> items = MapperUtils.findMDDistributionType(source);
        if (!items.isEmpty()) {
            for (MDDistributionType i : items) {
                i.getTransferOptions().forEach(transferOption -> transferOption.getMDDigitalTransferOptions().getOnLine().forEach(link -> {
                    if (link.getAbstractOnlineResource() != null && link.getAbstractOnlineResource().getValue() instanceof CIOnlineResourceType2 ciOnlineResource) {
                        if (ciOnlineResource.getLinkage().getCharacterString() != null && !ciOnlineResource.getLinkage().getCharacterString().getValue().toString().isEmpty()) {
                            LinkModel linkModel = LinkModel.builder().build();
                            if (ciOnlineResource.getProtocol() != null) {
                                linkModel.setType(Objects.equals(ciOnlineResource.getProtocol().getCharacterString().getValue().toString(), "WWW:LINK-1.0-http--link") ? "text/html" : "");
                            }
                            linkModel.setHref(ciOnlineResource.getLinkage().getCharacterString().getValue().toString());
                            linkModel.setRel(AppConstants.RECOMMENDED_LINK_REL_TYPE);
                            linkModel.setTitle(getOnlineResourceName(ciOnlineResource));
                            results.add(linkModel);
                        }
                    }
                }));
            }
        }

        // Now add links for logos
        geoNetworkService.getLogo(this.mapUUID(source))
                .ifPresent(results::add);

        // Thumbnail link
        geoNetworkService.getThumbnail(this.mapUUID(source))
                .ifPresent(results::add);

        // full metadata link
        var metadataSource = source.getMetadataLinkage();
        safeGet(() -> ((CIOnlineResourceType2) (metadataSource.get(0)
                .getAbstractOnlineResource()
                .getValue()))
                .getLinkage().getCharacterString().getValue().toString()
        ).ifPresent(url -> {
            LinkModel linkModel = LinkModel.builder()
                    .href(url)
                    .rel(RelationType.SELF.getValue())
                    .type(MediaType.TEXT_HTML.getValue())
                    .title("Full metadata link")
                    .build();
            results.add(linkModel);
        });

        // add license links
        var licenseLinks = getLicenseLinks(source);
        results.addAll(licenseLinks);

        return results;
    }

    private List<LinkModel> getLicenseLinks(MDMetadataType source) {
        List<LinkModel> links = new ArrayList<>();
        var dataIdentifications = MapperUtils.findMDDataIdentificationType(source);
        for (var dataIdentification : dataIdentifications) {
            if (dataIdentification.getResourceConstraints().isEmpty()) {
                continue;
            }
            for (var resourceConstraint : dataIdentification.getResourceConstraints()) {
                var legalConstraints = safeGet(() -> (MDLegalConstraintsType) resourceConstraint.getAbstractConstraints().getValue());
                if (legalConstraints.isEmpty()) {
                    continue;
                }

                // license graphic
                var graphic = getLicenseGraphic(legalConstraints.get());
                if (graphic != null) {
                    links.add(graphic);
                }

                // license url
                var url = getLicenseUrl(legalConstraints.get());
                if (url != null) {
                    links.add(url);
                }
            }
        }
        return links;
    }

    private LinkModel getLicenseGraphic(MDLegalConstraintsType legalConstraints) {
        var ciOnlineResource = safeGet(() -> {
            var onlineResource = legalConstraints.getGraphic().get(0)
                    .getMDBrowseGraphic().getLinkage().get(0)
                    .getAbstractOnlineResource().getValue();
            return (CIOnlineResourceType2) onlineResource;
        });
        if (ciOnlineResource.isEmpty()) {
            return null;
        }

        var graphic = safeGet(() -> ciOnlineResource.get().getLinkage()
                .getCharacterString().getValue().toString());

        return graphic.map(graphicUrl -> LinkModel.builder()
                .href(graphicUrl)
                .rel(RelationType.LICENSE.getValue())
                .type(MediaType.IMAGE_PNG.getValue())
                .build()).orElse(null);
    }

    private LinkModel getLicenseUrl(MDLegalConstraintsType legalConstraints) {
        var references = safeGet(legalConstraints::getReference);
        if (references.isEmpty()) {
            return null;
        }
        for (var reference : references.get()) {

            var url = safeGet(() -> {
                var ciCitation = (CICitationType2) reference.getAbstractCitation().getValue();
                return ciCitation.getOnlineResource().get(0)
                        .getCIOnlineResource().getLinkage().getCharacterString().getValue().toString();
            });
            if (url.isPresent()) {
                return LinkModel.builder()
                        .href(url.get())
                        .rel(RelationType.LICENSE.getValue())
                        .type(MediaType.TEXT_HTML.getValue())
                        .build();
            }
        }
        return null;
    }

    // TODO: need to handle exception
    @Named("mapProviders")
    List<ProviderModel> mapProviders(MDMetadataType source) {
        List<ProviderModel> results = new ArrayList<>();
        source.getContact().forEach(item -> {
            if (item.getAbstractResponsibility() != null) {
                if(item.getAbstractResponsibility().getValue() instanceof CIResponsibilityType2 ciResponsibility) {
                    ciResponsibility.getParty().forEach(party -> {
                        try
                        {
                            ProviderModel providerModel = ProviderModel.builder().build();
                            providerModel.setRoles(Collections.singletonList(ciResponsibility.getRole().getCIRoleCode().getCodeListValue()));
                            CIOrganisationType2 organisationType2 = (CIOrganisationType2) party.getAbstractCIParty().getValue();
                            providerModel.setName(organisationType2.getName() != null ? organisationType2.getName().getCharacterString().getValue().toString() : "");
                            organisationType2.getIndividual().forEach(individual -> individual.getCIIndividual().getContactInfo().forEach(contactInfo -> {
                                contactInfo.getCIContact().getOnlineResource().forEach(onlineResource -> {
                                    providerModel.setUrl(onlineResource.getCIOnlineResource().getLinkage().getCharacterString().getValue().toString());
                                });
                            }));
                            results.add(providerModel);
                        }
                        catch (ClassCastException e) {
                            logger.error("Unable to cast getAbstractCIParty().getValue() to CIOrganisationType2 for metadata record: {}", mapUUID(source));
                        }
                    });
                }
                else {
                    logger.warn("getContact().getAbstractResponsibility() in mapProviders is not of type CIResponsibilityType2 for UUID {}", mapUUID(source));
                }
            }
            else {
                logger.warn("Null value fround for getContact().getAbstractResponsibility() in mapProviders transform for UUID {}", mapUUID(source));
            }
        });
        return results;
    }

    @Named("mapLicense")
    String mapLicense(MDMetadataType source) {
        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
        List<String> licenses = new ArrayList<>();
        List<String> potentialKeys = Arrays.asList("license", "creative commons");
        if (!items.isEmpty()) {
            for (MDDataIdentificationType i : items) {
                i.getResourceConstraints().forEach(resourceConstraint -> {
                    if (resourceConstraint.getAbstractConstraints().getValue() instanceof MDLegalConstraintsType legalConstraintsType) {

                        // try to find licence in citation block first
                        var licencesInCitation = findLicenseInCitationBlock(legalConstraintsType);
                        if (!licencesInCitation.isEmpty()) {
                            licenses.addAll(licencesInCitation);
                        }

                        // Some organizations didn't put license in the citation block, so now try finding in different location
                        // (other constraints)if above didn't add any values to licenses array
                        if (licenses.isEmpty()) {
                            safeGet(legalConstraintsType::getOtherConstraints).ifPresent( otherConstraints -> {
                                otherConstraints.forEach( otherConstraint -> {
                                    var licenseTitle = safeGet(() -> otherConstraint.getCharacterString().getValue().toString());
                                    if (licenseTitle.isEmpty()) {
                                        return;
                                    }
                                    for (var potentialKey : potentialKeys) {
                                        if (licenseTitle.get().toLowerCase().contains(potentialKey)) {
                                            licenses.add(licenseTitle.get());
                                        }
                                    }
                                });
                            });
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

    private List<String> findLicenseInCitationBlock(MDLegalConstraintsType legalConstraintsType) {
        List<String> licenses = new ArrayList<>();
        if (safeGet(legalConstraintsType::getReference).isEmpty()) {
            return licenses;
        }
        legalConstraintsType.getReference().forEach(reference -> {

            var title = safeGet(() -> {
                var ciCitation = (CICitationType2) reference.getAbstractCitation().getValue();
                return ciCitation.getTitle().getCharacterString().getValue().toString();
            });
            if (title.isEmpty()) {
                return;
            }
            licenses.add(title.get());
        });
        return licenses;
    }

    /**
     * A sample of contact block will be like this, you can have individual block and organization block together
     *
     * @param source
     * @return
     */
    @Named("mapContacts")
    List<ContactsModel> mapContacts(MDMetadataType source) {
        List<ContactsModel> results = new ArrayList<>();

        // get about contacts
        List<MDDataIdentificationType> dataIdentificationTypeItems = MapperUtils.findMDDataIdentificationType(source);
        if (!dataIdentificationTypeItems.isEmpty()) {

            for (MDDataIdentificationType item : dataIdentificationTypeItems) {
                item.getPointOfContact().forEach(poc -> {
                    if (poc.getAbstractResponsibility() != null) {

                        AbstractResponsibilityType responsibilityType = poc.getAbstractResponsibility().getValue();
                        if (responsibilityType instanceof final CIResponsibilityType2 ciResponsibility) {

                            if (ciResponsibility.getParty().isEmpty()) {
                                logger.warn("Unable to find contact info for metadata record: " + this.mapUUID(source));
                            }
                            else {
                                ciResponsibility.getParty().forEach(party -> {

                                    // to tag data contacts (on the "about" panel)
                                    var mappedContacts = MapperUtils.mapOrgContacts(ciResponsibility, party);
                                    results.addAll(MapperUtils.addRoleToContacts(mappedContacts, "about"));
                                });
                            }
                        }
                    }
                    else {
                        logger.warn("getAbstractResponsibility() is null in mapContact for metadata record: {}", mapUUID(source));
                    }
                });
            }
        }

        // get metadata contact
        var mdContacts = MapperUtils.findMDContact(source);
        if (!mdContacts.isEmpty()) {
            for (var mdContact : mdContacts) {
                var responsibilityValue = mdContact.getAbstractResponsibility().getValue();
                if (
                        !(responsibilityValue instanceof final CIResponsibilityType2 ciResponsibility)
                                || ciResponsibility.getParty() == null) {
                    continue;
                }

                for (var party : ciResponsibility.getParty()) {

                    // to tag metadata contacts (on the "metadata" panel)
                    var mappedContacts = MapperUtils.mapOrgContacts(ciResponsibility, party);
                    results.addAll(MapperUtils.addRoleToContacts(mappedContacts, "metadata"));
                }
            }
        }

        // get citation contacts (cited responsible parties)
        if (!dataIdentificationTypeItems.isEmpty()) {
            for (var item : dataIdentificationTypeItems) {
                var citationType = safeGet(() -> item.getCitation().getAbstractCitation().getValue());
                if (citationType.isEmpty()) {
                    continue;
                }
                if (!(citationType.get() instanceof  CICitationType2 ciCitationType2)) {
                    continue;
                }
                var ciResponsProperties = safeGet(ciCitationType2::getCitedResponsibleParty);
                if (ciResponsProperties.isEmpty() || ciResponsProperties.get().isEmpty()) {
                    continue;
                }

                for (var property : ciResponsProperties.get()) {
                    var ciResponsibility = property.getCIResponsibility();

                    if (ciResponsibility.getParty().isEmpty()) {
                        logger.warn("Unable to find citation contact info for metadata record: " + this.mapUUID(source));
                    }
                    else {
                        ciResponsibility.getParty().forEach(party -> {
                            var mappedContacts = MapperUtils.mapOrgContacts(ciResponsibility, party);
                            results.addAll(MapperUtils.addRoleToContacts(mappedContacts, "citation"));
                        });
                    }
                }
            }
        }

        return results;
    }

    @Named("mapLanguages")
    protected List<LanguageModel> mapLanguages(MDMetadataType source) {
        List<LanguageModel> results = new ArrayList<>();
        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
        if (!items.isEmpty()) {
            for (MDDataIdentificationType i : items) {

                LanguageModel languageModel = LanguageModel.builder().build();

                String langCode = MapperUtils.mapLanguagesCode(i) != null ? MapperUtils.mapLanguagesCode(i) : "";
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

    protected <R> R createGeometryItems(
            MDMetadataType source,
            Function<List<List<AbstractEXGeographicExtentType>>, R> handler) {

        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
        if(!items.isEmpty()) {
            if(items.size() > 1) {
                logger.warn("!! More than 1 block of MDDataIdentificationType, data will be missed !!");
            }
            // Assume only 1 block of <mri:MD_DataIdentification>
            // We only concern geographicElement here
            List<EXExtentType> ext = items.get(0)
                    .getExtent()
                    .stream()
                    .filter(f -> f.getAbstractExtent() != null)
                    .filter(f -> f.getAbstractExtent().getValue() != null)
                    .filter(f -> f.getAbstractExtent().getValue() instanceof EXExtentType)
                    .map(f -> (EXExtentType) f.getAbstractExtent().getValue())
                    .filter(f -> f.getGeographicElement() != null)
                    .toList();

            // We want to get a list of item where each item contains multiple, (aka list) of
            // (EXGeographicBoundingBoxType or EXBoundingPolygonType)
            List<List<AbstractEXGeographicExtentType>> rawInput = ext.stream()
                    .map(EXExtentType::getGeographicElement)
                    .map(l ->
                            /*
                                l = List<AbstractEXGeographicExtentPropertyType>
                                For each AbstractEXGeographicExtentPropertyType, we get the tag that store the
                                coordinate, it is either a EXBoundingPolygonType or EXGeographicBoundingBoxType
                             */
                            l.stream()
                                    .map(AbstractEXGeographicExtentPropertyType::getAbstractEXGeographicExtent)
                                    .filter(Objects::nonNull)
                                    .filter(m -> (m.getValue() instanceof EXBoundingPolygonType || m.getValue() instanceof EXGeographicBoundingBoxType))
                                    .map(m -> {
                                        if (m.getValue() instanceof EXBoundingPolygonType exBoundingPolygonType) {
                                            if (!exBoundingPolygonType.getPolygon().isEmpty() && exBoundingPolygonType.getPolygon().get(0).getAbstractGeometry() != null) {
                                                return exBoundingPolygonType;
                                            }
                                        } else if (m.getValue() instanceof EXGeographicBoundingBoxType) {
                                            return m.getValue();
                                        }
                                        return null; // Handle other cases or return appropriate default value
                                    })
                                    .filter(Objects::nonNull) // Filter out null values if any
                                    .toList()
                    )
                    .toList();
            return handler.apply(rawInput);
        }
        return null;
    }
    /**
     * Special handle for MimeFileType object.
     * @param onlineResource
     * @return
     */
    protected String getOnlineResourceName(CIOnlineResourceType2 onlineResource) {
        if(onlineResource.getName() != null && onlineResource.getName().getCharacterString() != null) {
            if(onlineResource.getName().getCharacterString().getValue() instanceof MimeFileTypeType mt) {
                return mt.getValue();
            }
            else {
                return onlineResource.getName().getCharacterString().getValue().toString();
            }
        }
        else {
            return null;
        }
    }
}
