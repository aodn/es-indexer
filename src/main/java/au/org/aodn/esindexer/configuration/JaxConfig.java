package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.utils.JaxbUtils;
import jakarta.xml.bind.JAXBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import au.org.aodn.metadata.iso19115_3.*;

@Configuration
public class JaxConfig {
    @Bean("metadataJaxb")
    public JaxbUtils<MDMetadataType> createJaxbUtilsForMDMetadataType() throws JAXBException {
        return new JaxbUtils<>(MDMetadataType.class);
    }
}
