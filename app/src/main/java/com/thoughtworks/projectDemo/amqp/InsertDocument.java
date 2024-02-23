package com.thoughtworks.projectDemo.amqp;

import lombok.Data;

@Data
public class InsertDocument {
    private Long indexId;
    private String esIndexName;
    private String id;
    private String source;
}
