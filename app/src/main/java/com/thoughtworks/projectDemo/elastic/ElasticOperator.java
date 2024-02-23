package com.thoughtworks.projectDemo.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ElasticOperator {
    private final ElasticsearchClient elasticsearchClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void createIndex(String index) {
        if (elasticsearchClient.indices().exists(r -> r.index(index)).value()) {
            elasticsearchClient.indices().delete(r -> r.index(index));
        }
        elasticsearchClient.indices().create(r -> r.index(index));
    }

    @SneakyThrows
    public void reindex(String sourceIndex, String destIndex) {
        elasticsearchClient.reindex(r -> r.source(s -> s.index(sourceIndex)).dest(d -> d.index(destIndex)));
    }

    @SneakyThrows
    public void deleteIndex(String index) {
        elasticsearchClient.indices().delete(r -> r.index(index));
    }

    @SneakyThrows
    public void createMapping(String index, String propertyJson) {
        var request = new Request("PUT", index + "/_mapping");
        request.setJsonEntity(propertyJson);
        restClient.performRequest(request);
    }

    @SneakyThrows
    public void createDocument(String index, String documentJson) {
        var request = new Request("POST", "/" + index + "/_doc");
        request.setJsonEntity(documentJson);
        restClient.performRequest(request);
    }

    @SneakyThrows
    public void batchInsertDocument(List<ElasticDocument> elasticDocuments) {
        Request request = new Request("POST", "/_bulk");
        String bulkRequestBody = elasticDocuments.stream()
                .map(elasticDocument -> "{ \"index\" : { \"_index\" : \"" + elasticDocument.getIndex() + "\", \"_id\" : \"" + elasticDocument.getId() + "\" } }\n" + elasticDocument.buildHasSourceIdDocumentJson(objectMapper) + "\n")
                .reduce("", (a, b) -> a + b);
        request.setJsonEntity(bulkRequestBody);
        restClient.performRequest(request);
    }

    @SneakyThrows
    public void createDocument(ElasticDocument elasticDocument) {
        var request = new Request("PUT", "/" + elasticDocument.getIndex() + "/_doc" + "/" + elasticDocument.getId());
        request.setJsonEntity(elasticDocument.buildHasSourceIdDocumentJson(objectMapper));
        restClient.performRequest(request);
    }

    @SneakyThrows
    public ElasticDocument getDocument(String index, String id) {
        var request = new Request("Get", "/" + index + "/_doc" + "/" + id);
        Response response = restClient.performRequest(request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 200 && statusCode < 300) {
            var json = EntityUtils.toString(response.getEntity());
            var docResponse = objectMapper.readValue(json, DocResponse.class);
            var docJson = objectMapper.writeValueAsString(docResponse.getSource());
            return ElasticDocument.create(objectMapper, docResponse.getIndex(), docResponse.getId(), docJson);
        } else {
            throw new RuntimeException("Failed to perform request. Status code: " + statusCode);
        }
    }

    @SneakyThrows
    public List<ElasticDocument> searchDocument(String index, String query) {
        var request = new Request("GET", "/" + index + "/_search");
        request.setJsonEntity(query);
        Response response = restClient.performRequest(request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 200 && statusCode < 300) {
            // 从响应中获取内容
            String responseBody = EntityUtils.toString(response.getEntity());
            var searchResponse = objectMapper.readValue(responseBody, SearchResponse.class);
            return searchResponse.getHits().getHits().stream().map(hitsItem -> {
                try {
                    var docJson = objectMapper.writeValueAsString(hitsItem.getSource());
                    return ElasticDocument.builder().documentJson(docJson)
                            .id(hitsItem.getId()).index(hitsItem.getIndex()).build();
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        } else {
            throw new RuntimeException("Failed to perform request. Status code: " + statusCode);
        }
    }
}
