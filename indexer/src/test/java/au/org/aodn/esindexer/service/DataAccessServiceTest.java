package au.org.aodn.esindexer.service;

import au.org.aodn.cloudoptimized.model.CloudOptimizedEntry;
import au.org.aodn.cloudoptimized.model.CloudOptimizedEntryReducePrecision;
import au.org.aodn.stac.model.StacItemModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static au.org.aodn.esindexer.BaseTestClass.readResourceFile;

@Slf4j
public class DataAccessServiceTest {

    protected ObjectMapper objectMapper = new ObjectMapper();
    /**
     * We test CloudOptimizedEntryReducePrecision to reduce precision so that we can group accordingly
     * @throws IOException - Not expect to throw.
     */
    @Test
    public void verifyAggregateCorrect() throws IOException {
        // Our test do not care, pass null is ok
        DataAccessServiceImpl impl = new DataAccessServiceImpl(null, null, null);
        String canned_2024_01= readResourceFile("classpath:canned/dataservice/35234913-aa3c-48ec-b9a4-77f822f66ef8/2024-01.json");

        List<CloudOptimizedEntryReducePrecision> l = objectMapper.readValue(
                new StringReader(canned_2024_01),
                new TypeReference<List<CloudOptimizedEntryReducePrecision>>() {}
        );
        Map<? extends CloudOptimizedEntry, Long> result = impl.aggregateData(l);

        Assertions.assertEquals(478, result.size());

        // Find a target object and check its count
        CloudOptimizedEntryReducePrecision target = new CloudOptimizedEntryReducePrecision();
        target.setLongitude(155.0);
        target.setLatitude(-33.21);
        target.setDepth(500.0);
        target.setTime("2024-01-28 00:12:12");

        Assertions.assertEquals(14L, result.get(target), "Count is correct 1");

        target.setDepth(530.0);
        Assertions.assertEquals(15L, result.get(target), "Count is correct 2");
    }

    @Test
    public void verifyToStacItemModel() throws IOException {
        DataAccessServiceImpl impl = new DataAccessServiceImpl(null, null, null);
        String canned_2024_01= readResourceFile("classpath:canned/dataservice/35234913-aa3c-48ec-b9a4-77f822f66ef8/2024-02.json");

        List<CloudOptimizedEntryReducePrecision> l = objectMapper.readValue(
                new StringReader(canned_2024_01),
                new TypeReference<List<CloudOptimizedEntryReducePrecision>>() {}
        );

        Map<? extends CloudOptimizedEntry, Long> result = impl.aggregateData(l);
        List<StacItemModel> models = impl.toStacItemModel("35234913-aa3c-48ec-b9a4-77f822f66ef8", result);

        Assertions.assertEquals(270, models.size(), "Count correct");

        // StacItemModel(uuid=35234913-aa3c-48ec-b9a4-77f822f66ef8|2024-02|170.33|-33.87|530.0, geometry={geometry={coordinates=[170.33, -33.87], type=Point}, type=Feature, properties={depth=530.0}}, bbox=null, properties={time=2024-02, count=15}, links=null, assets=null, collection=35234913-aa3c-48ec-b9a4-77f822f66ef8)
        Optional<StacItemModel> t = models.stream()
                .filter(f -> f.getUuid().equalsIgnoreCase("35234913-aa3c-48ec-b9a4-77f822f66ef8|2024-02|170.33|-33.87|530.00"))
                .findFirst();

        Assertions.assertTrue(t.isPresent(), "Target found");
        Assertions.assertEquals(15L, t.get().getProperties().get("count"));

        Map<?, ?> properties = t.get().getProperties();
        // The depth is a BigDecimal, so we do a toString() will force it print the .00 which is what we want
        // to check it contains two decimal
        Assertions.assertEquals(530.0, properties.get("depth"));
        Assertions.assertEquals(170.33, properties.get("lng"));
    }
}
