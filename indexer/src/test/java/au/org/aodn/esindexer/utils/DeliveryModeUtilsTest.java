package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
public class DeliveryModeUtilsTest {

    @Test
    public void testNormaliseStatusWithCustomisedStatus() {
        // example: https://catalogue.aodn.org.au/geonetwork/srv/eng/catalog.search#/metadata/06bf0e30-2ae3-11dd-a735-00188b4c0af8
        String historicalArchiveInput = "historicalArchive";
        assertEquals("completed", DeliveryModeUtils.normaliseStatus(historicalArchiveInput), "historicalArchive should be normalized to completed");

        // example: https://catalogue.aodn.org.au/geonetwork/srv/eng/catalog.search#/metadata/e3715a57-69de-4013-b4d4-695ee5428fe4
        String obsoleteInput = "obsolete";
        assertEquals("completed", DeliveryModeUtils.normaliseStatus(obsoleteInput), "obsolete should be normalized to completed");

        // example: https://catalogue.aodn.org.au/geonetwork/srv/eng/catalog.search#/metadata/51a5b7e6-80a5-4e36-b3e2-2b558dd2b4aa
        String completeInput = "Complete";
        assertEquals("completed", DeliveryModeUtils.normaliseStatus(completeInput), "Complete should be normalized to completed");

        // example: https://catalogue.aodn.org.au/geonetwork/srv/eng/catalog.search#/metadata/638408f6-2e88-4d1b-a6ce-e342139a101d
        String onGoingInput = "on going";
        assertEquals("ongoing", DeliveryModeUtils.normaliseStatus(onGoingInput), "on going should be normalized to ongoing");

    }
}
