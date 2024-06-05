# `es-indexer` - Elasticsearch Indexer

![ESGN_Indexer](https://github.com/aodn/es-indexer/assets/26201635/99615859-b4a4-48be-a3af-72b7f1fc048f)

This `es-indexer` for ingesting GeoNetwork4 metadata records into an Elasticsearch index. The index schema adheres to the STAC schema but includes some customisations.

Although GeoNetwork4 itself comes with a default Elasticsearch index (`gn_records`), the OGC APIs will use the `es-indexer`-created index to retrieve data for the new AODN portal.

## Development

This application is built with `Spring Boot 3` and `Java 17`.

There are required environment variables to run the `es-indexer`:

```env
# Client calling the Indexer API must provide this token in the Authorization header, these value is set
# in [appdeply](https://github.com/aodn/appdeploy/blob/main/tg/edge/es-indexer/ecs/variables.yaml) for edge env
# under environment_variables:

APP_HTTP_AUTH_TOKEN=sampletoken

SERVER_PORT=8080

ELASTICSEARCH_INDEX_NAME=sampleindex
ELASTICSEARCH_SERVERURL=http://localhost:9200
ELASTICSEARCH_APIKEY=sampleapikey

GEONETWORK_HOST=http://localhost:8080
```

### Maven build

```console
mvn clean install
or
mvn clean install [-DskipTests]

# If you do not use skipTest, then autotest will run where it will create a docker geonetwork instance, inject the
sample data and then run the indexer. You can treat this as kind of integration testing.
```

This project container 3 submodules:
* geonetwork - This is used to compile JAXB lib to handle XML return from GEONetowrk, it is iso19115 standard
* stacmodel - A group of java class that create the STAC json which store in elastic search, so if app needs to read
STAC from elastic, use this lib
* indexer - The main app that do the transformation.

### Docker

```console
# Start a local instance of indexer

docker-compose -f docker-compose-dev.yaml up [-d: in daemon mode | --build: to see the console logs]
```

### Endpoints:

| Description                             | Endpoints                              | Environment |
|-----------------------------------------|----------------------------------------|-------------|
| Logfile                                 | `/manage/logfile`                      | Edge        |
| Beans info                              | `/manage/beans`                        | Edge        |
| Env info                                | `/manage/env`                          | Edge        |
| Info  (Show version)                    | `/manage/info`                         | Edge        |
| Health check                            | `/manage/health`                       | Edge        |
| POST/GET/DELETE against specific record | `/api/v1/indexer/index/{records-uuid}` | All         |
| Bulk index                              | `/api/v1/indexer/index/all`            | All         |
| Bulk index Async                        | `/api/v1/indexer/index//async/all      | All         |
| Swagger UI:                             | `/swagger-ui/index.html`               | All         |

> The 'async/all' endpoints use SSE (Server Side Events) to avoid gateway timeout, you should use
> postman version 10.2 or above (there is a bug with SSE for previous version), or use the web based
> postman (pref), once you issue the call, you should see event come back in the body at regular time.
>
> The call header should contains
> * X-API-Key  (Check with dev)
> * Accept = text/event-stream
> * Content-Type = text/event-stream;charset=utf-8
> * Method = POST
