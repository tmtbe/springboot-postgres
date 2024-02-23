package com.thoughtworks.projectDemo.amqp;

import lombok.Data;

import java.util.List;

@Data
public class BatchInsertDocument {
    private String batchId;
    private List<InsertDocument> documents;
}
