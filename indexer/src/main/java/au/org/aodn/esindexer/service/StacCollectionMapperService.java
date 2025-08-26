package au.org.aodn.esindexer.service;

import au.org.aodn.stac.model.RelationType;
import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.esindexer.utils.AssociatedRecordsUtil;
import au.org.aodn.esindexer.utils.*;
import au.org.aodn.metadata.geonetwork.service.GeoNetworkService;
import au.org.aodn.stac.model.*;

import au.org.aodn.metadata.geonetwork.GeoNetworkField;
import au.org.aodn.metadata.iso19115_3_2018.*;
import au.org.aodn.stac.util.JsonUtil;
import jakarta.xml.bind.JAXBElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.util.Tuple;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;
import static au.org.aodn.esindexer.utils.StringUtil.capitalizeFirstLetter;

/**
 * This class transform the XML from GeoNetwork to STAC format and store it into Elastic-Search
 */
@Service
@Mapper(componentModel = "spring")
public abstract class StacCollectionMapperService {
    @BeforeMapping
    void beforeMapping(MDMetadataType source) {
        logger.info("Processing mapping uuid: {}", CommonUtils.getUUID(source));
    }

    @Mapping(target="uuid", source = "source", qualifiedByName = "mapUUID")
    @Mapping(target="title", source = "source", qualifiedByName = "mapTitle")
    @Mapping(target="description", source = "source", qualifiedByName = "mapDescription")
    @Mapping(target="extent.bbox", source = "source", qualifiedByName = "mapExtentBbox")
    @Mapping(target="extent.temporal", source = "source", qualifiedByName = "mapExtentTemporal")
    @Mapping(target="contacts", source = "source", qualifiedByName = "mapContacts")
    @Mapping(target="themes", source = "source", qualifiedByName = "mapThemes")
    @Mapping(target="languages", source = "source", qualifiedByName = "mapLanguages")
    @Mapping(target="links", source = "source", qualifiedByName = "mapLinks")
    @Mapping(target="license", source = "source", qualifiedByName = "mapLicense")
    @Mapping(target="providers", source = "source", qualifiedByName = "mapProviders")
    @Mapping(target="citation", source="source", qualifiedByName = "mapCitation")
    @Mapping(target="assets", source = "source", qualifiedByName = "assets")
    @Mapping(target="summaries.status", source = "source", qualifiedByName = "mapSummaries.status")
    @Mapping(target="summaries.scope", source = "source", qualifiedByName = "mapSummaries.scope")
    @Mapping(target="summaries.credits", source = "source", qualifiedByName = "mapSummaries.credits")
    @Mapping(target="summaries.geometry", source = "source", qualifiedByName = "mapSummaries.geometry")
    @Mapping(target="summaries.geometryNoLand", source = "source", qualifiedByName = "mapSummaries.geometryNoland")
    @Mapping(target="summaries.temporal", source = "source", qualifiedByName = "mapSummaries.temporal")
    @Mapping(target="summaries.updateFrequency", source = "source", qualifiedByName = "mapSummaries.updateFrequency")
    @Mapping(target="summaries.datasetProvider", source = "source", qualifiedByName = "mapSummaries.datasetProvider")
    @Mapping(target="summaries.datasetGroup", source = "source", qualifiedByName = "mapSummaries.datasetGroup")
    @Mapping(target="summaries.statement", source="source", qualifiedByName = "mapSummaries.statement")
    @Mapping(target="summaries.creation", source = "source", qualifiedByName = "mapSummaries.creation")
    @Mapping(target="summaries.revision", source = "source", qualifiedByName = "mapSummaries.revision")
    public abstract StacCollectionModel mapToSTACCollection(MDMetadataType source);

    protected static final Logger logger = LogManager.getLogger(StacCollectionMapperService.class);

    @Value("${spring.jpa.properties.hibernate.jdbc.time_zone}")
    private String timeZoneId;

    @Autowired
    protected GeoNetworkService geoNetworkService;

    @Autowired
    protected DataAccessService dataAccessService;

    @Autowired
    protected IndexCloudOptimizedService indexCloudOptimizedService;

    @Named("mapUUID")
    String mapUUID(MDMetadataType source) {
        return CommonUtils.getUUID(source);
    }
    /**
     * According to the spec, the bbox must be an of length 2*n where n is number of dimension, so a 2D map, the
     * dimension is 4 and therefore it must be a box.
     *
     * @param source - The parsed XML
     * @return The list<BigDecimal> must be of size 4 due to 2D map.
     */
    @Named("mapExtentBbox")
    List<List<BigDecimal>> mapExtentBbox(MDMetadataType source) {
        return GeometryUtils.createGeometryItems(
                source,
                BBoxUtils::createBBoxFrom,
                null
        );
    }

    @Named("mapExtentTemporal")
    List<String[]> mapExtentTemporal(MDMetadataType source) {
        return TemporalUtils.concatOverallTemporalRange(createExtentTemporal(source));
    }

    List<String[]> createExtentTemporal(MDMetadataType source) {

        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
        if (items.isEmpty()) {
            logger.warn("Unable to find extent temporal information for metadata record: {}", CommonUtils.getUUID(source));
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
                        pair0.ifPresent(pair -> temporalPair[0] = convertDateToZonedDateTime(CommonUtils.getUUID(source), pair, true));

                        var pair1 = safeGet(() -> timePeriodType.getEnd().getTimeInstant().getTimePosition().getValue().get(0));
                        if (pair1.isEmpty()) {
                            pair1 = safeGet(() -> timePeriodType.getEndPosition().getValue().get(0));
                        }
                        pair1.ifPresent(pair -> temporalPair[1] = convertDateToZonedDateTime(CommonUtils.getUUID(source), pair, false));
                    }

                    result.add(temporalPair);
                });
            });
        }
        return result;
    }
    /**
     * If the date missing month / day / time then we will add it back by making it cover a range that is as wide as
     * possible. So for example if only year then it will be first date of year and end date of that year.
     *
     * @param uuid - The uuid of the record
     * @param dateStr - The date value in the XML
     * @param isStartDate - Is it processing start date?
     * @return - Well format date time string
     */
    private String convertDateToZonedDateTime(String uuid, String dateStr, boolean isStartDate) {
        ZonedDateTime utcZonedDateTime = null;
        String convertedDateTime;
        try {
            // Case 1: Date and Time (e.g., "2024-09-10T10:15:30")
            if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
                // Do nothing
                convertedDateTime = dateStr;
                ZonedDateTime zt = ZonedDateTime.parse(convertedDateTime, TemporalUtils.TIME_FORMATTER.withZone(ZoneId.of(timeZoneId)));
                utcZonedDateTime = zt.withZoneSameInstant(ZoneOffset.UTC);
            }
            // Case 2: Full Date (e.g., "2024-09-10"), depends on it is start or end, try to cover the full range
            else if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                convertedDateTime = isStartDate ? dateStr + "T00:00:00" : dateStr + "T23:59:59";
                ZonedDateTime zt = ZonedDateTime.parse(convertedDateTime, TemporalUtils.TIME_FORMATTER.withZone(ZoneId.of(timeZoneId)));
                utcZonedDateTime = zt.withZoneSameInstant(ZoneOffset.UTC);
            }
            // Case 3: Year and Month (e.g., "2024-09"), depends on it is start or end, try to cover the full range
            else if (dateStr.matches("\\d{4}-\\d{2}")) {
                YearMonth yearMonth = YearMonth.parse(dateStr);
                LocalDateTime ld = isStartDate ?
                        yearMonth.atDay(1).atTime(0, 0, 0) :
                        yearMonth.atEndOfMonth().atTime(23, 59, 59);

                ZonedDateTime zt =  ld.atZone(ZoneId.of(timeZoneId));
                utcZonedDateTime = zt.withZoneSameInstant(ZoneOffset.UTC);
            }
            // Case 4: Year only (e.g., "2024"), depends on it is start or end, try to cover the full range
            else if (dateStr.matches("\\d{4}")) {
                YearMonth yearMonth = isStartDate ? YearMonth.parse(dateStr + "-01") : YearMonth.parse(dateStr + "-12");
                LocalDateTime ld = isStartDate ?
                        yearMonth.atDay(1).atTime(0, 0, 0) :
                        yearMonth.atEndOfMonth().atTime(23, 59, 59);

                ZonedDateTime zt =  ld.atZone(ZoneId.of(timeZoneId));
                utcZonedDateTime = zt.withZoneSameInstant(ZoneOffset.UTC);
            }


            // Convert to UTC
            if(utcZonedDateTime != null) {
                return utcZonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
            else {
                logger.warn("Unable to convert date to ISO_OFFSET_DATE_TIME: {} for record {}", dateStr, uuid);
                return null;
            }
        }
        catch (Exception e) {
            logger.warn("Unable to convert date to ISO_OFFSET_DATE_TIME: {} for record {}", dateStr, uuid);
            return null;
        }
    }

    /**
     * Custom mapping for description field, name convention is start with map then the field name
     * @param source - The parsed XML
     * @return The description
     */
    @Named("mapDescription")
    String mapDescription(MDMetadataType source) {
        return CommonUtils.getDescription(source);
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

                    otherConstraints.forEach(constraint ->
                            safeGet(() -> constraint.getCharacterString().getValue().toString())
                                    .ifPresent(cons -> {
                                        // split into suggested citation text + remaining other constraints text: parts[0]=suggested, parts[1]=remaining
                                        String[] parts = extractCitationParts((String) cons);

                                        if (parts[0] != null && !parts[0].isBlank()) {
                                            if (citation.getSuggestedCitation() == null || citation.getSuggestedCitation().isBlank()) {
                                                citation.setSuggestedCitation(parts[0]);
                                            }
                                        }
                                        if (parts[1] != null && !parts[1].isBlank()) {
                                            citation.addOtherConstraint(parts[1]);
                                        }
                                    })
                    );
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
        return SummariesUtils.getStatement(source);
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
        // IMAS data has this identifier, e.g., https://geonetwork-edge.aodn.org.au/geonetwork/srv/eng/catalog.search#/metadata/20b07936-3bfb-4a72-805d-0b24f1fd4d3f/formatters/xsl-view?root=div&view=advanced
        // and https://catalogue.aodn.org.au/geonetwork/srv/eng/catalog.search#/metadata/697b1314-7351-4478-b5f4-7ca4e5a1db3a/formatters/imos-full-view?root=div&view=advanced&approved=true
        if (constraint.toLowerCase().contains("data accessed at")) {
            return true;
        }
        return false;
    }

    /**
     * This is a helper function addition to isSuggestedCitation function. This is because for CSIRO and AAD data, the suggested citation text is mixed with other constraints text.
     * We need to split them separeted, with specific identifiers.
     * Identifier for CSIRO data: "ATTRIBUTION STATEMENT", which is a sentence ends up with an end point.
     * Identifier for AAD data: "citation reference provided at ", which is a sentence normally ends up with an end point, but sometimes can be ended with a right brack ).
     * @param constraint the constraint text
     * @return [suggested, remaining] if matched the suggested citation pattern,
     */
    private static String[] extractCitationParts(String constraint) {
        if (constraint == null) return new String[]{null, null};

        // normalise whitespace
        String text = constraint.replace('\u00A0',' ').trim().replaceAll("\\s+", " ").trim();
        if (text.isBlank()) return new String[]{null, null};

        // CSIRO pattern: "ATTRIBUTION STATEMENT" sentence (e.g. https://geonetwork-edge.aodn.org.au/geonetwork/srv/eng/catalog.search#/metadata/207809c8-b555-37f5-e053-08114f8c1f41/formatters/xsl-view?root=div&view=advanced)
        Pattern csiroPattern = Pattern.compile("(?is)\\bATTRIBUTION\\s+STATEMENT:\\s*([^\\n\\r]+?)(?:[.!?]\\s*|$)");
        Matcher csiroMatcher = csiroPattern.matcher(text);
        if (csiroMatcher.find()) {
            String suggested = ("ATTRIBUTION STATEMENT: " + csiroMatcher.group(1)).trim();
            String remaining = (text.substring(0, Math.max(0, csiroMatcher.start())) + " " +
                    text.substring(Math.min(csiroMatcher.end(), text.length()))).trim();
            if (remaining.isEmpty()) remaining = null;
            return new String[]{suggested, remaining};
        }

        // AAD pattern: "Please follow instructions listed in the citation reference provided at URL when using these data", which is a sentence normally ends up with period, but sometimes can be ended with a right bracket.
        // example: https://geonetwork-edge.aodn.org.au/geonetwork/srv/eng/catalog.search#/metadata/AADC-00099/formatters/xsl-view?root=div&view=advanced
        Pattern aadPattern = Pattern.compile(
                "(?is)\\bplease\\s+follow\\s+instructions\\s+listed\\s+in\\s+the\\s+citation\\s+reference\\s+provided\\s+at\\s+(https?://\\S+?)\\s+when\\s+using\\s+these\\s+data[.)]?"
        );
        Matcher aadMatcher = aadPattern.matcher(text);
        if (aadMatcher.find()) {
            // URL captured in group 1
            String url = aadMatcher.group(1);

            // Find sentence boundaries like end point or right brack or new line
            int anchor = aadMatcher.start();
            int start = 0;
            for (int i = anchor - 1; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == '.' || c == ')' || c == '\n') { start = i + 1; break; }
            }
            int end = aadMatcher.end();

            // build suggested citation text
            String suggested = text.substring(start, end).trim();

            // If the sentence ends with '.' or ')' but the URL itself does not, trim the trailing char
            if (!url.endsWith(".") && suggested.endsWith(".")) {
                suggested = suggested.substring(0, suggested.length() - 1).trim();
            } else if (!url.endsWith(")") && suggested.endsWith(")")) {
                suggested = suggested.substring(0, suggested.length() - 1).trim();
            }

            // build remaining text to fall back to other constraints field
            String remaining = (text.substring(0, start) + " " + text.substring(end)).trim();
            if (remaining.isEmpty()) remaining = null;

            return new String[]{suggested, remaining};
        }

        // Fallback to isSuggestedCitation check
        if (isSuggestedCitation(text)) {
            return new String[]{text, null};
        }

        // If nothing matches suggested citation pattern, everything remains in other constraints
        return new String[]{null, text};
    }


    @Named("mapSummaries.temporal")
    List<Map<String,String>> mapSummariesTemporal(MDMetadataType source) {
        List<Map<String,String>> result = new ArrayList<>();
        List<String[]> temp = createExtentTemporal(source);

        if (temp != null) {
            for (String[] t : temp) {
                Map<String, String> temporal = new HashMap<>();
                temporal.put("start", t[0]);
                temporal.put("end", t[1]);

                result.add(temporal);
            }
        }
        else {
            // It is extreme important to return a structure of summaries.temporal:{start: null, end: null}
            // due to a elastic query will fail if the nested path summaries.temporal do not exist for example
            // if your query is having nested { path "summaries.temporal" } and you want to check if end exist,
            // this query failed and result in strange result if summaries.temporal do not event exist.
            result.add(new HashMap<>());
        }

        return result;
    }

    @Named("mapSummaries.creation")
    String mapSummariesCreation(MDMetadataType source) {
        var dateSources = MapperUtils.findMDDateInfo(source);
        var dateMap = getMetadataDateInfoFrom(dateSources);
        return safeGet(() -> dateMap.get(GeoNetworkField.creation)).orElse(null);
    }

    @Named("mapSummaries.revision")
    String mapSummariesRevision(MDMetadataType source) {
        var dateSources = MapperUtils.findMDDateInfo(source);
        var dateMap = getMetadataDateInfoFrom(dateSources);
        return safeGet(() -> dateMap.get(GeoNetworkField.revision)).orElse(null);
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
    /**
     * The spatial extends
     * @param source - The parsed XML
     * @return - The spatial extents without land
     */
    @Named("mapSummaries.geometryNoland")
    Map<?,?> mapSummariesGeometryNoLand(MDMetadataType source) {
        return GeometryUtils.createGeometryItems(
                source,
                GeometryUtils::createGeometryNoLandFrom,
                10
        );
    }

    @Named("mapSummaries.geometry")
    Map<?,?> mapSummariesGeometry(MDMetadataType source) {
        return GeometryUtils.createGeometryItems(
                source,
                GeometryUtils::createGeometryFrom,
                10  // This is useful in testing/edge only.
        );
    }

    @Named("mapSummaries.status")
    String createSummariesStatus(MDMetadataType source) {
        return SummariesUtils.getStatus(source);
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

        logger.warn("Unable to find scope metadata record: {}", CommonUtils.getUUID(source));
        return null;
    }
    /**
     * Custom mapping for title field, name convention is start with map then the field name
     * @param source - The parsed XML document
     * @return - The title
     */
    @Named("mapTitle")
    String mapTitle(MDMetadataType source) {
        return CommonUtils.getTitle(source);
    }
    /**
     * Map the field credits, it is under
     * <mri:MD_DataIdentification>
     *     <mri:credit>XXXXXX</mri:credit>
     *     <mri:credit>YYYYYY</mri:credit>
     * </mri:MD_DataIdentification>
     * @param source - The parsed XML
     * @return The title
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
     * @param source - The xml document
     * @return The delivery mode based on the logic
     */
    @Named("mapSummaries.updateFrequency")
    String mapUpdateFrequency(MDMetadataType source) {
        return DeliveryModeUtils.getDeliveryMode(source).toString();
    }
    /**
     * TODO: Very simple logic here, if provider name contains IMOS
     *
     * @param source - The parsed XML
     * @return - The dataset owner
     */
    @Named("mapSummaries.datasetProvider")
    String mapDatasetOwner(MDMetadataType source) {
        List<ProviderModel> providers = mapProviders(source);
        return providers.stream()
                .filter(p -> p.getName() != null)
                .anyMatch(p -> p.getName().contains("IMOS")) ? "IMOS" : null;
    }

    @Named("mapSummaries.datasetGroup")
    String mapGeoNetworkGroup(MDMetadataType source) {
        try {
            String group = geoNetworkService.findGroupById(CommonUtils.getUUID(source));
            return group != null ? group.toLowerCase() : null;
        }
        catch (IOException e) {
            return null;
        }
    }

    protected List<ConceptModel> mapThemesConcepts(MDKeywordsPropertyType descriptiveKeyword, String uuid) {
        final List<ConceptModel> concepts = new ArrayList<>();
        safeGet(() -> descriptiveKeyword.getMDKeywords().getKeyword())
                .ifPresent(p -> p.forEach(keyword -> {
                    if (keyword != null) {
                        ConceptModel conceptModel = ConceptModel.builder().build();
                        if (keyword.getCharacterString().getValue() instanceof AnchorType value) {
                            conceptModel.setId(value.getValue());
                            conceptModel.setUrl(value.getHref());
                        } else {
                            conceptModel.setId(keyword.getCharacterString().getValue().toString());
                        }
                        conceptModel.setTitle(mapThemesTitle(descriptiveKeyword, uuid));
                        conceptModel.setDescription(mapThemesDescription(descriptiveKeyword, uuid));
                        concepts.add(conceptModel);
                    }
                }
        ));
        return concepts;
    }

    protected String mapThemesTitle(MDKeywordsPropertyType descriptiveKeyword, String uuid) {
        return safeGet(() -> descriptiveKeyword.getMDKeywords().getThesaurusName().getAbstractCitation())
                .map(citation -> {
                    if(citation.getValue() instanceof CICitationType2 thesaurusNameType2) {
                        CharacterStringPropertyType titleString = thesaurusNameType2.getTitle();
                        if (titleString != null
                                && titleString.getCharacterString() != null
                                && titleString.getCharacterString().getValue() instanceof AnchorType value) {
                            return (value.getValue() != null ? value.getValue() : "");
                        }
                        else if (titleString != null
                                && titleString.getCharacterString() != null
                                && titleString.getCharacterString().getValue() instanceof String value) {
                            return value;
                        }
                    }
                    return "";
                })
                .orElseGet(() -> {
                    // If no thesaurusName, try to use type as title
                    // make sure it is really not a thesaurusName
                    if (safeGet(() -> descriptiveKeyword.getMDKeywords().getThesaurusName()).isEmpty()) {
                        var type = safeGet(() -> descriptiveKeyword.getMDKeywords().getType().getMDKeywordTypeCode().getCodeListValue());
                        if (type.isPresent()) {
                            if (type.get().isEmpty()) {
                                return "Keywords";
                            }
                            return String.format("Keywords (%s)", capitalizeFirstLetter(type.get()));
                        }

                    }
                    logger.debug("Unable to find themes' title for metadata record: {}", uuid);
                    return "";
                });
    }

    protected String mapThemesDescription(MDKeywordsPropertyType descriptiveKeyword, String uuid) {
        return safeGet(() -> descriptiveKeyword.getMDKeywords().getThesaurusName())
                .map(abstractCitationPropertyType ->
                    safeGet(() -> abstractCitationPropertyType.getAbstractCitation().getValue())
                            .map(v -> (CICitationType2)v)
                            .map(thesaurusNameType2 -> {
                                CharacterStringPropertyType titleString = thesaurusNameType2.getTitle();
                                if (titleString != null && titleString.getCharacterString().getValue() instanceof AnchorType value) {
                                    if (value.getTitleAttribute() != null) {
                                        return value.getTitleAttribute();
                                    } else {
                                        return "";
                                    }
                                }
                                else if (titleString != null && titleString.getCharacterString().getValue() instanceof String) {
                                    return thesaurusNameType2.getAlternateTitle().stream().map(CharacterStringPropertyType::getCharacterString).map(JAXBElement::getValue).map(Object::toString).collect(Collectors.joining(", "));
                                }
                                else {
                                    return "";
                                }
                            })
                            .orElseGet(() -> {
                                logger.debug("Unable to find abstract citation for metadata record: {}", uuid);
                                return "";
                            })
                )
                .orElseGet(() -> {
                    logger.debug("Unable to find themes' description for metadata record: {}", uuid);
                    return "";
                });

    }

    protected String mapThemesScheme(MDKeywordsPropertyType descriptiveKeyword, String uuid) {

        var thesaurusName = safeGet(() -> descriptiveKeyword.getMDKeywords().getThesaurusName());
        if (thesaurusName.isEmpty()) {
                    logger.debug("thesaurusName is not found when mapping theme scheme for metadata record: {}", uuid);
            return "";
        }
        var codeListValue = safeGet(() -> descriptiveKeyword.getMDKeywords()
                .getType().getMDKeywordTypeCode().getCodeListValue());
        if (codeListValue.isEmpty()) {
            logger.debug("codeListValue is not found when mapping theme scheme for metadata record: {}", uuid);
            return "";
        }

        return codeListValue.get();
    }

    @Named("mapThemes")
    List<ThemesModel> mapThemes(MDMetadataType source) {
        List<ThemesModel> results = new ArrayList<>();
        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
        if (!items.isEmpty()) {

            for (MDDataIdentificationType i : items) {

                // get keywords
                i.getDescriptiveKeywords().forEach(descriptiveKeyword -> {
                    ThemesModel themesModel = ThemesModel.builder().build();
                    String uuid = CommonUtils.getUUID(source);

                    themesModel.setConcepts(mapThemesConcepts(descriptiveKeyword, uuid));
                    themesModel.setScheme(mapThemesScheme(descriptiveKeyword, uuid));
                    results.add(themesModel);
                });

                // get categories
                if (i.getTopicCategory() != null) {

                    var themesModel = ThemesModel.builder().scheme("Categories").concepts(new ArrayList<ConceptModel>()).build();
                    for (var category : i.getTopicCategory()) {
                        var categoryValue = safeGet(() -> category.getMDTopicCategoryCode().value());
                        if (categoryValue.isPresent()) {
                            var concept = ConceptModel.builder().id(categoryValue.get()).build();
                            themesModel.getConcepts().add(concept);
                        }
                    }
                    if (!themesModel.getConcepts().isEmpty()) {
                        results.add(themesModel);
                    } else {
                        logger.debug("No categories found for metadata record: {}", CommonUtils.getUUID(source));
                    }
                }
            }

        }
        return results;
    }

    @Named("mapLinks")
    List<LinkModel> mapLinks(MDMetadataType source) {
        final List<LinkModel> results = new ArrayList<>();

        // distribution links
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

                            // an empty string by default
                            linkModel.setRel("");

                            // differentiate WMS, WFS and others
                            safeGet(() -> ciOnlineResource.getProtocol().getCharacterString().getValue().toString())
                                    .ifPresent(protocol -> {
                                        linkModel.setRel(LinkUtils.getRelationType(protocol));
                                    });

                            linkModel.setTitle(getOnlineResourceName(ciOnlineResource));
                            results.add(linkModel);
                        }
                    }
                }));
            }
        }

        // Now add links for logos
        geoNetworkService.getLogo(CommonUtils.getUUID(source))
                .ifPresent(results::add);

        // Thumbnail link
        geoNetworkService.getThumbnail(CommonUtils.getUUID(source))
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
                    .rel(RelationType.DESCRIBEDBY.getValue())
                    .type(MediaType.TEXT_HTML.toString())
                    .title("Full metadata link")
                    .build();
            results.add(linkModel);
        });

        // add license links
        var licenseLinks = getLicenseLinks(source);
        results.addAll(licenseLinks);

        // add associated record links
        var associatedRecords = getAssociatedRecords(source);
        results.addAll(associatedRecords);

        var notebook = getNotebookLink(source);
        if (notebook != null) {
            results.add(notebook);
        }

        // AI link enhancement will be handled in @AfterMapping to avoid duplicate calls
        return results;
    }

    private List<LinkModel> getAssociatedRecords(MDMetadataType source) {
        var associatedRecordsData = geoNetworkService.getAssociatedRecords(CommonUtils.getUUID(source));
        return AssociatedRecordsUtil.generateAssociatedRecords(associatedRecordsData);
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

    protected LinkModel getNotebookLink(MDMetadataType source) {
        String uuid = CommonUtils.getUUID(source);

        return dataAccessService.getNotebookLink(uuid)
                .map(i -> LinkModel.builder()
                        .href(i)
                        .rel(RelationType.RELATED.getValue())
                        .type("application/x-ipynb+json")
                        .title("Python notebook example")
                        .build())
                .orElse(null);
    }

    protected LinkModel getLicenseGraphic(MDLegalConstraintsType legalConstraints) {
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
                .type(MediaType.IMAGE_PNG.toString())
                .build()).orElse(null);
    }

    protected LinkModel getLicenseUrl(MDLegalConstraintsType legalConstraints) {
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
                        .type(MediaType.TEXT_HTML.toString())
                        .build();
            }
        }
        return null;
    }

    // TODO: need to handle exception
    @Named("mapProviders")
    List<ProviderModel> mapProviders(MDMetadataType source) {
        final List<ProviderModel> results = new ArrayList<>();
        safeGet(source::getContact)
                .ifPresent(c -> c
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(item -> item.getAbstractResponsibility() != null)
                        .forEach(item -> {
                            if(item.getAbstractResponsibility().getValue() instanceof CIResponsibilityType2 ciResponsibility) {
                                safeGet(ciResponsibility::getParty)
                                        .ifPresent(p -> p.forEach(party -> {
                                            if(party.getAbstractCIParty().getValue() instanceof CIOrganisationType2 organisationType2) {
                                                ProviderModel providerModel = ProviderModel.builder().build();
                                                providerModel.setRoles(Collections.singletonList(
                                                        ciResponsibility.getRole().getCIRoleCode() != null ?
                                                        ciResponsibility.getRole().getCIRoleCode().getCodeListValue() :
                                                        null
                                                ));
                                                providerModel.setName(organisationType2.getName() != null ? organisationType2.getName().getCharacterString().getValue().toString() : "");

                                                organisationType2.getIndividual().forEach(individual -> individual.getCIIndividual().getContactInfo().forEach(contactInfo -> {
                                                    contactInfo.getCIContact().getOnlineResource().forEach(onlineResource ->
                                                            providerModel.setUrl(onlineResource.getCIOnlineResource().getLinkage().getCharacterString().getValue().toString())
                                                    );
                                                }));
                                                results.add(providerModel);
                                            }
                                            else if(party.getAbstractCIParty().getValue() instanceof CIIndividualType2 individualType2) {

                                                // a special example for this block:: https://geonetwork-edge.edge.aodn.org.au/geonetwork/srv/eng/catalog.search#/metadata/201112060/formatters/xsl-view?root=div&view=advanced
                                                ProviderModel providerModel = ProviderModel.builder().build();
                                                safeGet(() -> ciResponsibility.getRole().getCIRoleCode().getCodeListValue())
                                                        .ifPresent(role -> providerModel.setRoles(Collections.singletonList(role)));
                                                safeGet(() -> individualType2.getName().getCharacterString().getValue().toString())
                                                        .ifPresent(providerModel::setName);
                                                results.add(providerModel);
                                            }
                                            else {
                                                logger.error("Unable to cast getAbstractCIParty().getValue() to CIOrganisationType2 or CIIndividualType2 for metadata record: {}", CommonUtils.getUUID(source));
                                            }
                                        }));
                            }
                            else {
                                logger.warn("getContact().getAbstractResponsibility() in mapProviders is not of type CIResponsibilityType2 for UUID {}", CommonUtils.getUUID(source));
                            }
                        }));
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
            logger.debug("Unable to find license information for metadata record: {}", CommonUtils.getUUID(source));
            return "";
        }
    }

    private List<String> findLicenseInCitationBlock(MDLegalConstraintsType legalConstraintsType) {
        List<String> licenses = new ArrayList<>();
        safeGet(legalConstraintsType::getReference)
                .ifPresent(i -> {
                    i.forEach(reference -> {
                        var title = safeGet(() -> {
                            var ciCitation = (CICitationType2) reference.getAbstractCitation().getValue();
                            return ciCitation.getTitle().getCharacterString().getValue().toString();
                        });
                        if (title.isEmpty()) {
                            return;
                        }
                        licenses.add(title.get());
                    });
                });
        return licenses;
    }

    /**
     * A sample of contact block will be like this, you can have individual block and organization block together
     *
     * @param source - Parsed XML
     * @return - The Contract Model
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
                                logger.warn("Unable to find contact info for metadata record: {}", CommonUtils.getUUID(source));
                            }
                            else {
                                ciResponsibility.getParty().forEach(party -> {

                                    // to tag data contacts (on the "about" panel)
                                    var mappedContacts = MapperUtils.mapPartyContacts(ciResponsibility, party);
                                    results.addAll(MapperUtils.addRoleToContacts(mappedContacts, "about"));
                                });
                            }
                        }
                    }
                    else {
                        logger.warn("getAbstractResponsibility() is null in mapContact for metadata record: {}", CommonUtils.getUUID(source));
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
                    var mappedContacts = MapperUtils.mapPartyContacts(ciResponsibility, party);
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
                    final var ciResponsibility = property.getCIResponsibility();
                    safeGet(() -> property.getCIResponsibility().getParty())
                            .ifPresent(parties -> {
                                if (parties.isEmpty()) {
                                    logger.warn("Unable to find citation contact info for metadata record: {}", CommonUtils.getUUID(source));
                                } else {
                                    parties.forEach(party -> {
                                        var mappedContacts = MapperUtils.mapPartyContacts(ciResponsibility, party);
                                        results.addAll(MapperUtils.addRoleToContacts(mappedContacts, "citation"));
                                    });
                                }
                            });
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
                if(langCode != null) {
                    switch (langCode) {
                        case "eng" -> languageModel.setName("English");
                        case "fra" -> languageModel.setName("French");
                        default -> {
                            logger.warn("Unable to find language for metadata record: {}, default to eng", CommonUtils.getUUID(source));
                            languageModel.setCode("eng");
                            languageModel.setName("English");
                        }
                    }
                }
                results.add(languageModel);
            }
        }

        return results;
    }

    @Named("assets")
    protected Map<String, AssetModel> mapAssetsData(MDMetadataType source) {
        String collectionId = CommonUtils.getUUID(source);
        if(indexCloudOptimizedService.hasIndex(collectionId)) {
            var hitId = indexCloudOptimizedService.getHitId(collectionId);
            var mediaType = MediaType.APPLICATION_JSON_VALUE;
            if(hitId == null) {
                logger.error("Unable to find hitId for collection: {}", collectionId);
            }
            else if (hitId.contains(".parquet")) {
                mediaType = "application/x-parquet";
            }
            else if (hitId.contains(".zarr")) {
                mediaType = "application/x-zarr";
            }

            Map.Entry<String, AssetModel> entry = Map.entry(
                    AssetModel.Role.SUMMARY.toString(),
                    AssetModel.builder()
                            .role(AssetModel.Role.SUMMARY)
                            .type(mediaType)
                            .href(String.format("/collections/%s/items/summary", collectionId))
                            .title("Summary")
                            .description("Summary of cloud optimized data points")
                            .build()

            );
            return Map.ofEntries(entry);
        }
        else {
            return null;
        }
    }
    /**
     * Special handle for MimeFileType object.
     * @param onlineResource - The parsed XML that contains the target object
     * @return - The online resource
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
