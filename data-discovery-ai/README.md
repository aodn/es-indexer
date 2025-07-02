# Data Discovery AI Module

This module provides AI-enhanced functionalities for STAC collections in the es-indexer project.

## Overview

The Data Discovery AI module integrates with a remote AI service to enhance metadata links with semantic grouping
information. When processing metadata during indexing, the service calls the remote AI API to analyze links,
descriptions, themes... and return AI-generated content.

## Configuration

Add the following configuration to your `application.yaml` or `application.properties`:

```yaml
datadiscoveryai:
  host: https://data-discovery-ai-edge.aodn.org.au # AI service server
  baseUrl: /api/v1/ml/process_record # API base path
  apiKey: YOUR_API_KEY_HERE # API key for authentication
```

## API Contract

### Request Format

```json
{
  "selected_model": ["link_grouping"],
  "uuid": "dataset-uuid",
  "links": [
    {
      "href": "https://example.com/data",
      "rel": "data",
      "type": "application/json",
      "title": "Data Link"
    }
  ]
}
```

### Response Format

```json
{
  "links": [
    {
      "href": "https://example.com/data",
      "rel": "data",
      "type": "application/json",
      "title": "Data Link",
      "ai:group": "Document"
    }
  ]
}
```

## Service Endpoints

The AI service is expected to provide:

- `POST {baseUrl}/process_record` - Main enhancement endpoint
- `GET {baseUrl}/health` - Health check endpoint

## Error Handling

The module implements robust error handling:

- **Service Unavailable**: Returns original links if AI service is down
- **Network Errors**: Logs errors and falls back to original links
- **Invalid Responses**: Handles malformed responses gracefully
- **Timeouts**: Configurable connection and read timeouts (30s connect, 60s read)

## Dependencies

- Spring Boot Web Starter
- Spring Boot Auto Configuration
- Jackson for JSON processing
- STAC Model (internal dependency)
- Lombok for boilerplate reduction

## Architecture

- **Service Interface**: `DataDiscoveryAiService`
- **Implementation**: `DataDiscoveryAiServiceImpl`
- **Auto Configuration**: `DataDiscoveryAiAutoConfiguration`
- **Models**: Request/response DTOs in the `model` package
- **Integration**: Injected into `StacCollectionMapperService`
