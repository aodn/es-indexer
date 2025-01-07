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

    /**
     * This processor help to shield the complexity of bulk save of Elastic search where the batch size cannot be
     * too large. You keep calling the processItem by adding it new item. Once it reached the max size it will flush
     * to the Elastic, then it will reset and allow adding new item.
     * You must call flush at the end so that any remain item will get push
     * @param <T>
     */
    protected class BulkRequestProcessor<T> {
        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
        IndexService proxyImpl;
        Callback callback;
        Function<BulkResponseItem, Optional<T>> mapper;
        String indexName;
        long dataSize = 0;
        long total = 0;

        BulkRequestProcessor(String indexName, Function<BulkResponseItem, Optional<T>> mapper, IndexService proxyImpl, Callback callback) {
            this.indexName = indexName;
            this.proxyImpl = proxyImpl;
            this.mapper = mapper;
            this.callback = callback;
        }

        Optional<BulkResponse> processItem(String id, T item) throws IOException {
            if(item != null) {
                int size = indexerObjectMapper.writeValueAsBytes(item).length;

                // We need to split the batch into smaller size to avoid data too large error in ElasticSearch,
                // the limit is 10mb, so to make check before document add and push batch if size is too big
                //
                // dataSize = 0 is init case, just in case we have a very big doc that exceed the limit
                // and we have not add it to the bulkRequest, hardcode to 5M which should be safe,
                // usually it is 5M - 15M
                //
                if (dataSize + size > IndexServiceImpl.this.getBatchSize() && dataSize != 0) {
                    if (callback != null) {
                        callback.onProgress(String.format("Execute batch as bulk request is big enough %s", dataSize + size));
                    }

                    Optional<BulkResponse> result = Optional.of(reduceResponse(proxyImpl.executeBulk(bulkRequest, mapper, callback)));

                    dataSize = 0;
                    bulkRequest = new BulkRequest.Builder();

                    return result;
                }
                // Add item to  bulk request to Elasticsearch
                bulkRequest.operations(op -> op
                        .index(idx -> idx
                                .id(id)
                                .index(indexName)
                                .document(item)
                        )
                );
                dataSize += size;
                total++;

                if (callback != null) {
                    callback.onProgress(
                            String.format(
                                    "Add uuid %s to batch, batch size is %s, total is %s",
                                    id,
                                    dataSize,
                                    total)
                    );
                }
            }
            return Optional.empty();
        }

        Optional<BulkResponse> flush() throws IOException {
            return Optional.of(reduceResponse(proxyImpl.executeBulk(bulkRequest, mapper, callback)));
        }
    }

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

    @Override
    public long getBatchSize() {
        return 5242880;
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
