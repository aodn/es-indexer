package au.org.aodn.esindexer.utils;

import jakarta.xml.bind.*;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

public class JaxbUtils<T> {

    protected final Unmarshaller jaxbUnmarshaller;

    public JaxbUtils(Class<T> clazz) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    }

    @SuppressWarnings("unchecked")
    public T unmarshal(String input) throws JAXBException {
        try(StringReader reader = new StringReader(input)) {
            Source source = new StreamSource(reader);
            synchronized (jaxbUnmarshaller) {
                return ((JAXBElement<T>) jaxbUnmarshaller.unmarshal(source)).getValue();
            }
        }
    }
}
