# `es-indexer` - Elasticsearch Indexer

![ESGN_Indexer](https://github.com/aodn/es-indexer/assets/26201635/99615859-b4a4-48be-a3af-72b7f1fc048f)

This `es-indexer` for ingesting GeoNetwork4 metadata records into an Elasticsearch index. The index schema adheres to the STAC schema but includes some customisations.

Although GeoNetwork4 itself comes with a default Elasticsearch index (`gn_records`), the OGC APIs will use the `es-indexer`-created index to retrieve data for the new AODN portal.

## Development

This application is built with `Spring Boot 3` and `Java 17`.

There are required environment variables to run the `es-indexer`:

```env
# Client calling the Indexer API must provide this token in the Authorization header
APP_HTTP_AUTH_TOKEN=sampletoken

SERVER_PORT=8080

ELASTICSEARCH_INDEX_NAME=sampleindex
ELASTICSEARCH_SERVERURL=http://localhost:9200
ELASTICSEARCH_APIKEY=sampleapikey

GEONETWORK_HOST=http://localhost:8080
```

### Maven build

```console
mvn clean install [-DskipTests]
```

### Docker

```console
docker-compose -f docker-compose-dev.yaml up [-d: in daemon mode | --build: to see the console logs]
```

### Endpoints:

- Health check: `/actuator/health`
- POST/GET/DELETE against specific record: `/api/v1/indexer/index/{records-uuid}`
- Bulk index: `/api/v1/indexer/index/all`
- Swagger UI: `/swagger-ui/index.html`
