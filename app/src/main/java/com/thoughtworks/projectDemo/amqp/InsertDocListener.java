package com.thoughtworks.projectDemo.amqp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.elastic.ElasticDocument;
import com.thoughtworks.projectDemo.elastic.ElasticOperator;
import com.thoughtworks.projectDemo.elastic.Hits;
import com.thoughtworks.projectDemo.model.DocumentModel;
import com.thoughtworks.projectDemo.tables.daos.DocDao;
import com.thoughtworks.projectDemo.tables.pojos.Doc;
import lombok.AllArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RabbitListener(bindings = @QueueBinding(
        value = @Queue("insertDocQueue"),
        exchange = @Exchange(value = "insertDocExchange", type = ExchangeTypes.DIRECT),
        key = "insert.doc"
))
@AllArgsConstructor
public class InsertDocListener {
    private final ElasticOperator elasticOperator;
    private final DocDao docDao;
    @RabbitHandler
    public void onMessage(@Payload BatchInsertDocument batchInsertDocument) {
        List<InsertDocument> documents = batchInsertDocument.getDocuments();
        // 确保数据库能插入进去
        docDao.insert(documents.stream().map(it->{
            var docRecord = new Doc();
            docRecord.setBatchId(batchInsertDocument.getBatchId());
            docRecord.setSource(it.getSource());
            docRecord.setIndexId(it.getIndexId());
            return docRecord;
        }).toList());
        // es有可能插入会失败，比如这个index重建了。
        elasticOperator.batchInsertDocument(documents.stream()
                .map(it-> ElasticDocument.builder().index(it.getEsIndexName()).id(it.getId()).documentJson(it.getSource()).build())
                .toList());
    }
}
