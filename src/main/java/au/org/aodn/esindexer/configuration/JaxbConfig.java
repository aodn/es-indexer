package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.utils.JaxbUtils;
import jakarta.xml.bind.JAXBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JaxbConfig {
    @Bean("metadataJaxb")
    public JaxbUtils<au.org.aodn.metadata.iso19115_3_2018.MDMetadataType> createJaxbUtilsForMDMetadataType() throws JAXBException {
        return new JaxbUtils<>(au.org.aodn.metadata.iso19115_3_2018.MDMetadataType.class);
    }

    @Bean("metadataJaxb.2018")
    public JaxbUtils<au.org.aodn.metadata.iso19115_3.MDMetadataType> createJaxbUtilsForMDMetadataType2018() throws JAXBException {
        return new JaxbUtils<>(au.org.aodn.metadata.iso19115_3.MDMetadataType.class);
    }
}
