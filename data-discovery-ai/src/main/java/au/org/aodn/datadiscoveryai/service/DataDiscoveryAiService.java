package au.org.aodn.datadiscoveryai.service;

import au.org.aodn.datadiscoveryai.model.AiEnhancedLink;
import au.org.aodn.datadiscoveryai.model.AiEnhancementResponse;
import au.org.aodn.stac.model.LinkModel;

import java.util.List;

public interface DataDiscoveryAiService {

    /**
     * Enhances the provided links with AI-generated grouping information
     *
     * @param uuid  The UUID of the dataset
     * @param links The original links from the STAC collection
     * @return Enhanced links with AI grouping information
     */
    List<LinkModel> enhanceWithLinkGrouping(String uuid, List<LinkModel> links);

    /**
     * Enhances the provided content with AI-generated grouping and description formatting
     *
     * @param uuid  The UUID of the dataset
     * @param links The original links from the STAC collection (can be null)
     * @param title The title of the dataset (can be null)
     * @param description The description of the dataset (can be null)
     * @return AI enhancement response with enhanced links and summaries
     */
    AiEnhancementResponse enhanceWithAi(String uuid, List<LinkModel> links, String title, String description);

    /**
     * Check if the Data Discovery AI service is available
     *
     * @return true if service is available, false otherwise
     */
    boolean isServiceAvailable();

    /**
     * Converts AI-enhanced links to LinkModel objects
     * This is a utility method for other services that need to convert AI response links
     *
     * @param aiEnhancedLinks The AI-enhanced links from the AI service response
     * @return List of LinkModel objects with AI grouping information
     */
    List<LinkModel> convertAiLinksToLinkModels(List<AiEnhancedLink> aiEnhancedLinks);
}
