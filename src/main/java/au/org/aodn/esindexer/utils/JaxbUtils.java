package au.org.aodn.esindexer.utils;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.io.File;

public class JaxbUtils<T> {

    protected Unmarshaller jaxbUnmarshaller;

    public JaxbUtils(Class<T> clazz) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    }

    public T unmarshal(File f) throws JAXBException {
        synchronized (jaxbUnmarshaller) {
            return ((JAXBElement<T>)jaxbUnmarshaller.unmarshal(f)).getValue();
        }
    }
}
