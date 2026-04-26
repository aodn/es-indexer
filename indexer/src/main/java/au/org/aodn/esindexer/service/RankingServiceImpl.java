package au.org.aodn.esindexer.service;

import au.org.aodn.stac.model.ThemesModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import au.org.aodn.stac.model.StacCollectionModel;

import java.util.List;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

@Slf4j
@Service
public class RankingServiceImpl implements RankingService {

    @Value("${app.ranking.citation.weight:15}")
    protected int citationWeigth;

    @Value("${app.ranking.license.weight:10}")
    protected int licenseWeigth;

    @Value("${app.ranking.description.weight:0.01F}")
    protected float descriptionWeigth;

    @Value("${app.ranking.lineage.weight:10}")
    protected int lineageWeigth;

    @Value("${app.ranking.theme.minWeight:10}")
    protected int themeMinWeigth;

    @Value("${app.ranking.theme.midWeight:15}")
    protected int themeMidWeigth;

    @Value("${app.ranking.theme.maxWeight:20}")
    protected int themeMaxWeigth;

    @Value("${app.ranking.link.minWeight:10}")
    protected int linkMinWeigth;

    @Value("${app.ranking.link.midWeight:15}")
    protected int linkMidWeigth;

    @Value("${app.ranking.link.maxWeight:20}")
    protected int linkMaxWeigth;

    @Value("${app.ranking.imos.weight:10}")
    protected int imosWeigth;

    @Value("${app.ranking.downloadable.weight:10}")
    protected int downloadableWeigth;

    @Value("${app.ranking.codownload.weight:20}")
    protected int cloudOptimizedWeigth;

    @Value("${app.ranking.superseded.penalty:-10}")
    protected int supersededPenalty;

    public Integer evaluateCompleteness(StacCollectionModel stacCollectionModel) {
        int total = 0;
        int count = 0;
       /*
        * The implementation of this method can be adjusted
        * https://github.com/aodn/backlog/issues/5233
        * The richer the abstract the better
        *
        * Most should have a geometry , but document do not have , most around 90% so not too useful. Number of spatial extents also not good indicator.
        *
        * Geometry and spatial extents is use to identify a doc vs a metadata record about data, so it is not so used. And
        * there are other ways to identify docs which may be someone what to do it in some stage. The other method is use
        * the field -> Type of resource, -> Hierarchy level -> Resource scope ->Document
        *
        * Theme is not a good indicator
        * Link is more important even 1
        * Use of keywords, the more the better
        * if use Resource lineage then a good record
        * Data with resource constraint , license etc is a good record
        * For contact, everyone need to have it, but effort to put under citation is a better record
        */
        // Keywords store in theme
        if (stacCollectionModel.getThemes() != null && !stacCollectionModel.getThemes().isEmpty()) {
            List<ThemesModel> originalThemes = stacCollectionModel.getThemes().stream()
                    .filter(theme -> theme.getConcepts().stream()
                            // exclude AI predicted keywords
                            .noneMatch(concept -> concept.getAiDescription() != null))
                    .toList();
            log.debug("Keywords found with size: {}", originalThemes.size());
            if (!originalThemes.isEmpty()) {
                if (originalThemes.size() <= 2)      total += themeMinWeigth;
                else if (originalThemes.size() <= 5) total += themeMidWeigth;
                else                                 total += themeMaxWeigth;
                count++;
            }
        }
        // Lineage
        if (safeGet(() -> stacCollectionModel.getSummaries().getStatement())
                // empty string should not add up score
                .filter(s -> !s.isBlank())
                .isPresent()) {
            log.debug("Lineage found");
            total += lineageWeigth;
            count++;
        }
        // License
        if (stacCollectionModel.getLicense() != null) {
            log.debug("License found");
            total += licenseWeigth;
            count++;
        }
        // Constraint (citation)
        if (stacCollectionModel.getCitation() != null) {
            log.debug("Citation found");
            total += citationWeigth;
            count++;
        }
        // Abstract
        if (stacCollectionModel.getDescription() != null && !stacCollectionModel.getDescription().isBlank()) {
            log.debug("Description found");
            int w = (int) (stacCollectionModel.getDescription().length() * descriptionWeigth);
            total += Math.min(w, 25);
            count++;
        }
        // Links
        if (stacCollectionModel.getLinks() != null && !stacCollectionModel.getLinks().isEmpty()) {
            log.debug("Links found with size: {}", stacCollectionModel.getLinks().size());
            if (stacCollectionModel.getLinks().size() <= 2) {
                total += linkMinWeigth;
            }
            else if (stacCollectionModel.getLinks().size() <= 5) {
                total += linkMidWeigth;
            }
            else {
                total += linkMaxWeigth;
            }
            count++;
        }
        // IMOS record dataset_group = ["IMOS"]
        if (safeGet(() -> stacCollectionModel.getSummaries().getDatasetGroup())
                .filter(g -> g.size() == 1 && "IMOS".equalsIgnoreCase(g.get(0)))
                .isPresent()) {
            log.debug("IMOS owned record");
            total += imosWeigth;
        }
        // Cloud-optimised download service: assets populated means cloud-optimised index exists
        if (stacCollectionModel.getAssets() != null && !stacCollectionModel.getAssets().isEmpty()) {
            log.debug("Record has cloud optimised link");
            total += cloudOptimizedWeigth;
        }
        // Has downloadable links (this will include WFS download)
        if (stacCollectionModel.getLinks() != null
                && stacCollectionModel.getLinks().stream().anyMatch(link ->
                link.getAiRole() != null && link.getAiRole().contains("download"))) {
            log.debug("Record has downloadable links");
            total += downloadableWeigth;
        }
        // Penalty for superseded record: status equals superseded / deprecated / obsolete
        if (safeGet(() -> stacCollectionModel.getSummaries().getStatus())
                .map(String::toLowerCase)
                .filter(s -> s.contains("superseded") || s.contains("deprecated")
                        || s.contains("obsolete")   || s.contains("historicalarchive"))
                .isPresent()) {
            total += supersededPenalty;
        }
        log.debug("Overall count of metadata elements:{}", count);
        // The more field exist, the higher the mark
        return total + count;
    }
}
