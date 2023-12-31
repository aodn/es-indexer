package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.model.StacCollectionModel;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import au.org.aodn.metadata.iso19115_3_2018.*;

public interface StacCollectionMapperService {
    StacCollectionModel mapToSTACCollection(MDMetadataType source) throws FactoryException, TransformException;
}
