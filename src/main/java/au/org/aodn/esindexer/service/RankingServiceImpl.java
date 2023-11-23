package au.org.aodn.esindexer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import au.org.aodn.esindexer.model.StacCollectionModel;

@Service
public class RankingServiceImpl implements RankingService {

    protected static Logger logger = LoggerFactory.getLogger(RankingServiceImpl.class);

    public Integer evaluateCompleteness(StacCollectionModel stacCollectionModel) {
        Integer total = 0;

        /*
        * The implementation of this method can be adjusted
        * Current scoring system is (well, I made it up! feel free to change it)
        * 1. 15 points for title
        * 2. 15 points for description
        * 3. 10 points for extent geometry
        * 4. 10 points for extent temporal
        * 5a. 10 points for links with just 1-2 link
        * * 5b. 15 points for links with 3-5 links
        * * 5c. 20 points for links more than 5 links
        * 6a. 10 points for themes with just 1-2 themes
        * * 6b. 15 points for themes with 3-5 themes
        * * 6c. 20 points for themes more than 5 themes
        * 7. 10 points for contacts
        * Total: 100 points
        * */

        if (stacCollectionModel.getTitle() != null && !stacCollectionModel.getTitle().equals("")) {
            logger.debug("Title found");
            total += 15;
        }

        if (stacCollectionModel.getDescription() != null && !stacCollectionModel.getDescription().equals("")) {
            logger.debug("Description found");
            total += 15;
        }

        if (stacCollectionModel.getExtent().getBbox() != null) {
            logger.debug("Extent found");
            total += 10;
        }

        if (stacCollectionModel.getExtent().getTemporal() != null) {
            logger.debug("Temporal found");
            total += 10;
        }

        if (stacCollectionModel.getLinks() != null && stacCollectionModel.getLinks().size() != 0) {
            if (stacCollectionModel.getLinks().size() <= 2) {
                logger.debug("Links found with size: " + stacCollectionModel.getLinks().size());
                total += 10;
            } else if (stacCollectionModel.getLinks().size() <= 5) {
                logger.debug("Links found with size: " + stacCollectionModel.getLinks().size());
                total += 15;
            } else {
                logger.debug("Links found with size: " + stacCollectionModel.getLinks().size());
                total += 20;
            }
        }

        if (stacCollectionModel.getThemes() != null && stacCollectionModel.getThemes().size() != 0) {
            if (stacCollectionModel.getThemes().size() <= 2) {
                logger.debug("Themes found with size: " + stacCollectionModel.getThemes().size());
                total += 10;
            } else if (stacCollectionModel.getThemes().size() <= 5) {
                logger.debug("Themes found with size: " + stacCollectionModel.getThemes().size());
                total += 15;
            } else {
                logger.debug("Themes found with size: " + stacCollectionModel.getThemes().size());
                total += 20;
            }
        }

        if (stacCollectionModel.getContacts() != null && stacCollectionModel.getContacts().size() != 0) {
            logger.debug("Contacts found");
            total += 10;
        }

        return total;
    }
}
