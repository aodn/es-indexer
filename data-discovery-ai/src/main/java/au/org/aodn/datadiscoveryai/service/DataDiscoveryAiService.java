package au.org.aodn.datadiscoveryai.service;

import au.org.aodn.datadiscoveryai.model.AiEnhancedLink;
import au.org.aodn.datadiscoveryai.model.AiEnhancementRequest;
import au.org.aodn.datadiscoveryai.model.AiEnhancementResponse;
import au.org.aodn.stac.model.LinkModel;

import java.util.List;

public interface DataDiscoveryAiService {

    /**
     * Enhances the provided content with AI-generated grouping and description formatting
     *
     * @param aiEnhancementRequest  The request sent to AI service for record enhancement
     * @return AI enhancement response with enhanced links and summaries
     */
    AiEnhancementResponse enhanceWithAi(AiEnhancementRequest aiEnhancementRequest);

    /**
     * Check if the Data Discovery AI service is available
     *
     * @return true if service is available, false otherwise
     */
    boolean isServiceAvailable();


    List<LinkModel> getEnhancedLinks(AiEnhancementResponse aiResponse);

    String getEnhancedDescription(AiEnhancementResponse aiResponse);

    String getEnhancedUpdateFrequency(AiEnhancementResponse aiResponse);

    /**
     * Converts AI-enhanced links to LinkModel objects
     * This is a utility method for other services that need to convert AI response links
     *
     * @param aiEnhancedLinks The AI-enhanced links from the AI service response
     * @return List of LinkModel objects with AI grouping information
     */
    List<LinkModel> convertAiEnhancedLinksToLinkModels(List<AiEnhancedLink> aiEnhancedLinks);
}
