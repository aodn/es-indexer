package au.org.aodn.cloudoptimized.service;

import au.org.aodn.cloudoptimized.configuration.DataAccessServiceTestConfig;
import au.org.aodn.cloudoptimized.model.TemporalExtent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = DataAccessServiceTestConfig.class)
public class DataAccessServiceImplIT {

    @Autowired
    RestTemplate mockRestTemplate;

    @Autowired
    WebClient webClient;

    @Autowired
    DataAccessService dataAccessService;

    @BeforeEach
    void setUp() {
        reset(mockRestTemplate);
    }

    @Test
    void testGetTemporalExtentOf_retriesOnFailure() {
        String uuid = "testUuid";
        String key = "testKey";
        var expected = List.of(TemporalExtent.builder()
                .startDate("2020-01-01T00:00:00Z")
                .endDate("2020-12-31T23:59:59Z")
                .build());

        // First call throws, second call returns success
        when(mockRestTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class),
                anyMap()
        ))
                .thenThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,  "Temporary error", null, null, null))
                .thenReturn(new ResponseEntity<>(expected, HttpStatus.OK));

        // Call the method under test
        List<TemporalExtent> result = dataAccessService.getTemporalExtentOf(uuid, key);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("2020-01-01T00:00:00Z", result.get(0).getStartDate());
        assertEquals("2020-12-31T23:59:59Z", result.get(0).getEndDate());
        verify(mockRestTemplate, times(2)).exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class),
                anyMap()
        );
    }
}
