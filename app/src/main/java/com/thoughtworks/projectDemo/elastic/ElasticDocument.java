package com.thoughtworks.projectDemo.elastic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

@Setter
@Getter
@Builder
public class ElasticDocument {
    private String index;
    private String id;
    private String documentJson;
    // 在doc数据库里的原始文档id
    private Long sourceId;

    @SneakyThrows
    public static ElasticDocument create(ObjectMapper objectMapper, String index, String id, String documentJson) {
        var builder = ElasticDocument.builder().index(index).id(id);
        var tree = objectMapper.readTree(documentJson);
        builder.sourceId(tree.get("_source_id").asLong());
        var objectNode = objectMapper.convertValue(tree, ObjectNode.class);
        objectNode.remove("_source_id");
        builder.documentJson(objectMapper.writeValueAsString(objectNode));
        return builder.build();
    }

    @SneakyThrows
    public String buildHasSourceIdDocumentJson(ObjectMapper objectMapper) {
        var tree = objectMapper.readTree(documentJson);
        var objectNode = objectMapper.convertValue(tree, ObjectNode.class);
        objectNode.put("_source_id", sourceId);
        return objectMapper.writeValueAsString(objectNode);
    }
}
