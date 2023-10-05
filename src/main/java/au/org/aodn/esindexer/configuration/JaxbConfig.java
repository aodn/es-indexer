package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.utils.JaxbUtils;
import jakarta.xml.bind.JAXBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
@Configuration
public class JaxbConfig {
    @Bean("metadataJaxb.2018")
    public JaxbUtils<MDMetadataType> createJaxbUtilsForMDMetadataType2018() throws JAXBException {
        return new JaxbUtils<>(MDMetadataType.class);
    }
}
