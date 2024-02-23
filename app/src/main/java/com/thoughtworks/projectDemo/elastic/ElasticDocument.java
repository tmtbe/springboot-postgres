package com.thoughtworks.projectDemo.elastic;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ElasticDocument {
    private String index;
    private String id;
    private String documentJson;
    private String originalJson;
}
