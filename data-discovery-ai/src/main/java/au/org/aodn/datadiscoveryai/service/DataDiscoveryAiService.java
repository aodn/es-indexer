package au.org.aodn.datadiscoveryai.service;

import au.org.aodn.stac.model.LinkModel;

import java.util.List;

public interface DataDiscoveryAiService {
    
    /**
     * Enhances the provided links with AI-generated grouping information
     * 
     * @param uuid The UUID of the dataset
     * @param links The original links from the STAC collection
     * @return Enhanced links with AI grouping information
     */
    List<LinkModel> enhanceLinksWithAiGrouping(String uuid, List<LinkModel> links);
    
    /**
     * Check if the Data Discovery AI service is available
     * 
     * @return true if service is available, false otherwise
     */
    boolean isServiceAvailable();
} 
