package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.service.GeoNetworkService;
import au.org.aodn.esindexer.service.IndexerService;
import au.org.aodn.esindexer.service.StacCollectionMapperService;
import au.org.aodn.esindexer.utils.JaxbUtils;
import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import au.org.aodn.stac.model.StacCollectionModel;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/indexer/report")
@Tag(name="Indexer", description = "The Indexer API")
@Slf4j
public class ReportController {

    @Autowired
    IndexerService indexerService;

    @Autowired
    GeoNetworkService geonetworkResourceService;

    @Autowired
    StacCollectionMapperService stacCollectionMapperService;

    @Autowired
    JaxbUtils<MDMetadataType> jaxbUtils;
    /**
     * A report to show the diff between uuid in elastic index (target) vs geonetwork (source)
     * @param beginWithUuid - You want to start load with particular uuid, it is useful for resume previous incomplete reload
     * @return - A list of uuid
     */
    @GetMapping(path="/uuid-diff", produces = "application/json")
    public ResponseEntity<List<String>> reportUuidDiff(
            @RequestParam(value = "beginWithUuid", required=false) String beginWithUuid) throws JAXBException {

        List<String> result = new ArrayList<>();
        Iterable<String> docs = geonetworkResourceService.getAllMetadataRecords(beginWithUuid);

        for(String doc: docs) {
            MDMetadataType metadataType = jaxbUtils.unmarshal(doc);
            String uuid = stacCollectionMapperService.mapUUID(metadataType);

            try {
                ObjectNode response = indexerService.getDocumentByUUID(uuid).source();
                log.info("UUID OK {}", uuid);
            }
            catch (Exception e) {
                log.warn("UUID missing {}", uuid);
                result.add(uuid);
            }
        }
        return ResponseEntity.ok().body(result);
    }

}
