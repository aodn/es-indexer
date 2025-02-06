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

```bash
$ mvn clean install # [-DskipTests]
```

If you do not use `-DskipTests`, then autotest will run where it will create a docker geonetwork instance, inject the
sample data and then run the indexer. You can treat this as kind of integration testing.

This project container 3 submodules:
* **geonetwork** - This is used to compile JAXB lib to handle XML return from GEONetowrk, it is iso19115 standard
* **stacmodel** - A group of java class that create the STAC json which store in elastic search, so if app needs to read
  STAC from elastic, use this lib
* **indexer** - The main app that do the transformation.

### Docker

Start a local instance of indexer

```bash
$ docker-compose -f docker-compose-dev.yaml up # [-d: in daemon mode | --build: to see the console logs]
```

### Endpoints:

| Description                                            | Endpoints                              | Environment | Param                                                                   |
|--------------------------------------------------------|----------------------------------------|-------------|-------------------------------------------------------------------------|
| Logfile                                                | `/manage/logfile`                      | Edge        |                                                                         |
| Beans info                                             | `/manage/beans`                        | Edge        |                                                                         |
| Env info                                               | `/manage/env`                          | Edge        |                                                                         |
| Info  (Show version)                                   | `/manage/info`                         | Edge        |                                                                         |
| Health check                                           | `/manage/health`                       | Edge        |                                                                         |
| POST/GET/DELETE index metadata against specific record | `/api/v1/indexer/index/{uuid}`         | All         | withCO - set true will call index cloud optimized before index metadata |
| POST Index cloud optimized data on specific record     | `/api/v1/indexer/index/{uuid}/cloud    | All         |                                                                         |
| Bulk index                                             | `/api/v1/indexer/index/all`            | All         |                                                                         |
| Bulk index Async metadata on all                       | `/api/v1/indexer/index/async/all       | All         |                                                                         |
| POST Index Async cloud optimized data on all           | `/api/v1/indexer/index/async/all-cloud | All         |                                                                         |
| Swagger UI:                                            | `/swagger-ui/index.html`               | All         |                                                                         |

> The 'async/all' endpoints use SSE (Server Side Events) to avoid gateway timeout, you should use
> postman version 10.2 or above (there is a bug with SSE for previous version), or use the web based
> postman (pref), once you issue the call, you should see event come back in the body at regular time.
>
> The call header should contains
> * X-API-Key  (Check with dev)
> * Accept = text/event-stream
> * Content-Type = text/event-stream;charset=utf-8
> * Method = POST

## Notes
### Centroid Calculation
Centroid is calculated on the fly
1. Use the shape file contains the land only area, adjust it to reduce the line complexity
2. Remove the land from the spatial extents of each record
3. Store it in a field geometry_noland
4. If user request Centroid field, the BBOX area that user requested is UNION with geometry_noland.
5. This gives the current visible area of the spatial extents excluded land area
6. Then centroid is calculated dynamically based on these areas and return via API call
* Noted the speed of transfer geometry_noload to OGC api is the key bottleneck for performance.
