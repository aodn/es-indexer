package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import au.org.aodn.stac.model.SpatialExtentModel;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static au.org.aodn.esindexer.BaseTestClass.readResourceFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpatialExtentUtilsTest {

    protected JaxbUtils<MDMetadataType> jaxb;

    public SpatialExtentUtilsTest() throws JAXBException {
        jaxb = new JaxbUtils<>(MDMetadataType.class);
    }

    @Test
    public void verifyOneEntryPerDescription() throws IOException, JAXBException {
        List<SpatialExtentModel> extents = SpatialExtentUtils.createSpatialExtentsFrom(readRecord("sample7.xml"));

        assertEquals(11, extents.size());
        assertEquals("Dungeness", extents.get(0).getDescription());
        assertBbox(142.9417419434, -9.998605505, 142.9417419434, -9.998605505, extents.get(0).getBbox());
    }

    @Test
    public void verifyEmptyWhenNoDescription() throws IOException, JAXBException {
        assertTrue(SpatialExtentUtils.createSpatialExtentsFrom(readRecord("sample4.xml")).isEmpty());
    }

    // one description, two points: the bbox wraps both, portal matches points to it by containment
    @Test
    public void verifySharedDescriptionBbox() throws IOException, JAXBException {
        List<SpatialExtentModel> extents = SpatialExtentUtils.createSpatialExtentsFrom(
                readRecord("sample_spatial_extent_shared_description.xml"));

        assertEquals(1, extents.size());
        assertEquals("Two moorings", extents.get(0).getDescription());
        assertBbox(142.0, -19.0, 146.0, -10.0, extents.get(0).getBbox());
    }

    private MDMetadataType readRecord(String fileName) throws IOException, JAXBException {
        return jaxb.unmarshal(readResourceFile("classpath:canned/" + fileName));
    }

    private static void assertBbox(double west, double south, double east, double north, List<BigDecimal> bbox) {
        assertEquals(west, bbox.get(0).doubleValue(), 0.0000000001, "west");
        assertEquals(south, bbox.get(1).doubleValue(), 0.0000000001, "south");
        assertEquals(east, bbox.get(2).doubleValue(), 0.0000000001, "east");
        assertEquals(north, bbox.get(3).doubleValue(), 0.0000000001, "north");
    }
}
