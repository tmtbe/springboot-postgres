package com.thoughtworks.projectDemo.amqp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.elastic.ElasticDocument;
import com.thoughtworks.projectDemo.elastic.ElasticOperator;
import com.thoughtworks.projectDemo.enums.IndexStatus;
import com.thoughtworks.projectDemo.model.IndexModel;
import com.thoughtworks.projectDemo.model.PropertyModel;
import com.thoughtworks.projectDemo.service.IndexService;
import com.thoughtworks.projectDemo.service.VerifyDocService;
import com.thoughtworks.projectDemo.tables.daos.DocDao;
import com.thoughtworks.projectDemo.tables.daos.IndexDao;
import com.thoughtworks.projectDemo.tables.pojos.Doc;
import lombok.AllArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static com.thoughtworks.projectDemo.Tables.DOC;
import static com.thoughtworks.projectDemo.tables.Index.INDEX;

@Component
@RabbitListener(bindings = @QueueBinding(
        value = @Queue("recreateIndexQueue"),
        exchange = @Exchange(value = "recreateIndexExchange", type = ExchangeTypes.DIRECT),
        key = "index.recreate"
))
@AllArgsConstructor
public class RecreateIndexListener {
    private final IndexDao indexDao;
    private final IndexService indexService;
    private final ElasticOperator elasticOperator;
    private final DocDao docDao;
    @RabbitHandler
    public void onMessage(@Payload String indexName) {
        try {
            final int pageSize = 10; // 每页的大小
            int pageNumber = 1; // 页数
            boolean hasNextPage = true;
            var indexModel = indexService.mustGetIndexModel(indexName);
            while (hasNextPage) {
                // 构建分页查询并执行
                var docs = docDao.ctx().select().from(DOC)
                        .limit(pageSize).offset((pageNumber - 1) * pageSize)
                        .fetchInto(Doc.class);
                var batchDoc = indexService.processInsertDocument(indexModel, docs.stream().map(it-> ElasticDocument.builder().index(indexModel.getEsIndexName())
                        .originalJson(it.getSource())
                        .documentJson(it.getSource()).build()).toList(),true);
                elasticOperator.batchInsertDocument(batchDoc);
                // 检查是否有下一页
                hasNextPage = docs.size() == pageSize;
                pageNumber++;
            }
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            var indexRecord = indexDao.fetchOne(INDEX.NAME, indexName);
            assert indexRecord != null;
            indexRecord.setStatus(IndexStatus.Activated);
            indexDao.update(indexRecord);
        }
    }
}
