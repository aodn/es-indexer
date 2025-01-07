package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public abstract class IndexServiceImpl implements IndexService {

    protected static final long DEFAULT_BACKOFF_TIME = 3000L;
    protected ElasticsearchClient elasticClient;
    protected ObjectMapper indexerObjectMapper;

    public IndexServiceImpl(ElasticsearchClient elasticClient, ObjectMapper indexerObjectMapper) {
        this.elasticClient = elasticClient;
        this.indexerObjectMapper = indexerObjectMapper;
    }

    public static BulkResponse reduceResponse(BulkResponse in) {
        List<BulkResponseItem> errors = in.items()
                .stream()
                .filter(p -> !(p.status() == HttpStatus.CREATED.value() || p.status() == HttpStatus.OK.value()))
                .toList();

        return errors.isEmpty() ?
                BulkResponse.of(f -> f.items(new ArrayList<>()).errors(false).took(in.took())) :
                BulkResponse.of(f -> f.items(errors).errors(true).took(in.took()));
    }

    /**
     * Keep retry until success, it is ok to insert docs to elastic again because we use _id as identifier.
     * In case any service is not available, we will keep retry many times, with 100 retry we try 25 mins which is
     * big enough for aws process restart.
     *
     * @param bulkRequest - The bulk request
     * @param callback - The event call back to avoid timeout
     * @return - The bulk insert result
     * @throws IOException - Exceptions on error
     * @throws HttpServerErrorException.ServiceUnavailable - Exceptions on geonetwork die or elastic not available
     */
    @Retryable(
            retryFor = {Exception.class, HttpServerErrorException.ServiceUnavailable.class},
            maxAttempts = 1000,
            backoff = @Backoff(delay = DEFAULT_BACKOFF_TIME)
    )
    @Override
    public <T> BulkResponse executeBulk(BulkRequest.Builder bulkRequest, Function<BulkResponseItem, Optional<T>> mapper, IndexerMetadataService.Callback callback) throws IOException, HttpServerErrorException.ServiceUnavailable {
        try {
            // Keep retry until success
            BulkResponse result = elasticClient.bulk(bulkRequest.build());

            // Flush after insert, otherwise you need to wait for next auto-refresh. It is
            // especially a problem with autotest, where assert happens very fast.
            elasticClient.indices().refresh();

            // Report status if success
            if(callback != null) {
                callback.onProgress(reduceResponse(result));
            }

            // Log errors, if any
            if (!result.items().isEmpty()) {
                if (result.errors()) {
                    log.error("Bulk load have errors? {}", true);
                } else {
                    log.info("Bulk load have errors? {}", false);
                }
                for (BulkResponseItem item : result.items()) {
                    if (item.error() != null) {
                        Optional<T> i = mapper.apply(item);
                        i.ifPresent(a -> {
                            try {
                                log.error("UUID {} {} {} {}",
                                        item.id(),
                                        item.error().reason(),
                                        item.error().causedBy(),
                                        indexerObjectMapper
                                                .writerWithDefaultPrettyPrinter()
                                                .writeValueAsString(a)
                                );
                            } catch (JsonProcessingException e) {
                                log.error("Fail to convert item with json mapper, {}", item.id());
                            }
                        });
                    }
                }
            }
            return result;
        }
        catch(Exception e) {
            // Report status if not success, this help to keep connection
            if(callback != null) {
                callback.onProgress("Exception on bulk save, will retry : " + e.getMessage());
            }
            throw e;
        }
    }
}
