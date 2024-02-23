package com.thoughtworks.projectDemo.amqp;

import com.thoughtworks.projectDemo.model.DocumentModel;
import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class AmqpService {
    private final RabbitTemplate rabbitTemplate;
    public void insertDoc(BatchInsertDocument batchInsertDocument) {
        rabbitTemplate.convertAndSend("insertDocExchange", "insert.doc", batchInsertDocument);
    }

    public void recreateIndex(String indexName) {
        rabbitTemplate.convertAndSend("recreateIndexExchange", "index.recreate", indexName);
    }
}
