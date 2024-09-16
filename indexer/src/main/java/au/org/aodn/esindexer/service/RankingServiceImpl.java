package au.org.aodn.esindexer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import au.org.aodn.stac.model.StacCollectionModel;

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

    public Integer evaluateCompleteness(StacCollectionModel stacCollectionModel) {
        int total = 0;

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
            log.debug("Keywords found with size: {}", stacCollectionModel.getThemes().size());
            if (stacCollectionModel.getThemes().size() <= 2) {
                total += themeMinWeigth;
            } else if (stacCollectionModel.getThemes().size() <= 5) {
                total += themeMidWeigth;
            } else {
                total += themeMaxWeigth;
            }
        }
        // Lineage
        if (stacCollectionModel.getSummaries() != null && stacCollectionModel.getSummaries().getStatement() != null) {
            log.debug("Lineage found");
            total += lineageWeigth;
        }
        // License
        if (stacCollectionModel.getLicense() != null) {
            log.debug("License found");
            total += licenseWeigth;
        }
        // Constraint
        if (stacCollectionModel.getCitation() != null) {
            log.debug("Citation found");
            total += citationWeigth;
        }
        // Abstract
        if (stacCollectionModel.getDescription() != null && !stacCollectionModel.getDescription().isBlank()) {
            log.debug("Description found");
            total += (int) (stacCollectionModel.getDescription().length() * descriptionWeigth);
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
        }

        return total;
    }
}
