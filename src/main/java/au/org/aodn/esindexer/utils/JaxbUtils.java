package au.org.aodn.esindexer.utils;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

public class JaxbUtils<T> {

    protected Unmarshaller jaxbUnmarshaller;

    public JaxbUtils(Class<T> clazz) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    }

    public T unmarshal(String input) throws JAXBException {
        Source source = new StreamSource(new StringReader(input));
        synchronized (jaxbUnmarshaller) {
            return ((JAXBElement<T>)jaxbUnmarshaller.unmarshal(source)).getValue();
        }
    }
}
