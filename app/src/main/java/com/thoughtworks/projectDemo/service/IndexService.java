package com.thoughtworks.projectDemo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thoughtworks.projectDemo.Tables;
import com.thoughtworks.projectDemo.convert.DataMapper;
import com.thoughtworks.projectDemo.elastic.ElasticDocument;
import com.thoughtworks.projectDemo.elastic.ElasticOperator;
import com.thoughtworks.projectDemo.enums.IndexStatus;
import com.thoughtworks.projectDemo.model.DocumentModel;
import com.thoughtworks.projectDemo.model.IndexModel;
import com.thoughtworks.projectDemo.model.MappingModel;
import com.thoughtworks.projectDemo.model.PropertyModel;
import com.thoughtworks.projectDemo.tables.daos.*;
import com.thoughtworks.projectDemo.tables.pojos.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.thoughtworks.projectDemo.Tables.INDEX_DOC_RECORD;
import static com.thoughtworks.projectDemo.Tables.INDEX_PROPERTY;
import static com.thoughtworks.projectDemo.tables.Collection.COLLECTION;
import static com.thoughtworks.projectDemo.tables.Doc.DOC;
import static com.thoughtworks.projectDemo.tables.Index.INDEX;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexService {
    private final IndexDao indexDao;
    private final IndexPropertyDao indexPropertyDao;
    private final IndexDocRecordDao indexDocRecordDao;
    private final DocDao docDao;
    private final ObjectMapper objectMapper;
    private final ElasticOperator elasticOperator;
    private final VerifyDocService verifyDocService;
    private final JobService jobService;
    private final DataMapper dataMapper;
    private final CollectionDao collectionDao;

    public IndexModel mustGetIndexModel(String indexName) {
        var index = mustGetIndex(indexName);
        return buildIndexModel(List.of(index)).get(0);
    }

    private Index mustGetIndex(String indexName) {
        return indexDao.fetchOptional(INDEX.NAME, indexName).orElseThrow(() -> new RuntimeException("Index not found"));
    }

    private Boolean isIndexExist(String indexName) {
        return indexDao.fetchOptional(INDEX.NAME, indexName).isPresent();
    }


    public List<Index> getIndices(Long collectionId) {
        return indexDao.fetchByCollectionId(collectionId);
    }

    public List<IndexModel> getIndexModeList(Long collectionId) {
        var indexList = indexDao.fetchByCollectionId(collectionId);
        return buildIndexModel(indexList);
    }

    private List<IndexModel> buildIndexModel(List<Index> indexList) {
        var indexPropertyList = indexPropertyDao.ctx()
                .selectFrom(INDEX_PROPERTY)
                .where(INDEX_PROPERTY.INDEX_ID.in(indexList.stream().map(Index::getId).toList()))
                .fetchInto(IndexProperty.class);
        return indexList.stream().map(index -> {
            var indexModel = dataMapper.toIndexModel(index);
            var mappingModel = new MappingModel();
            mappingModel.setProperties(indexPropertyList.stream()
                    .filter(property -> property.getIndexId().equals(index.getId()))
                    .map(dataMapper::toPropertyModel).toList());
            indexModel.setMapping(mappingModel);
            return indexModel;
        }).toList();
    }

    @Transactional
    public void deleteIndex(String indexName, Boolean retainData) {
        var index = mustGetIndex(indexName);
        assert index.getStatus() != null;
        if (index.getStatus().equals(IndexStatus.Migrating)) {
            throw new RuntimeException("Index is migrating, can not delete");
        }
        elasticOperator.deleteIndex(index.getEsIndexName());
        indexDao.delete(index);
        if (!retainData) {
            // 不保留数据则会删除doc中关于这个index修改的数据
            docDao.ctx().deleteFrom(DOC)
                    .where(DOC.MODIFY_BY_INDEX.eq(index.getId()))
                    .execute();
        }
        indexPropertyDao.ctx().deleteFrom(INDEX_PROPERTY)
                .where(INDEX_PROPERTY.INDEX_ID.eq(index.getId()))
                .execute();
        indexDocRecordDao.ctx().deleteFrom(INDEX_DOC_RECORD)
                .where(INDEX_DOC_RECORD.INDEX_ID.eq(index.getId()))
                .execute();
    }

    private String getEsIndexName(String indexName) {
        return mustGetIndex(indexName).getEsIndexName();
    }

    public void createIndex(IndexModel indexModel) {
        if (isIndexExist(indexModel.getName())) {
            throw new RuntimeException("Index already exists");
        }
        if (indexModel.getCollectionId() == null && indexModel.getCollectionName() == null) {
            throw new RuntimeException("CollectionId and CollectionName can not be null at the same time");
        }
        checkIndexProperties(indexModel);
        Collection collection = null;
        if (indexModel.getCollectionName() != null) {
            collection = collectionDao.fetchOptional(COLLECTION.NAME, indexModel.getCollectionName()).orElseThrow(() -> new RuntimeException("Collection not found"));
        } else if (indexModel.getCollectionId() != null) {
            collection = collectionDao.fetchOptionalById(indexModel.getCollectionId()).orElseThrow(() -> new RuntimeException("Collection not found"));
            ;
        }
        assert collection != null;
        indexModel.setCollectionName(collection.getName());
        indexModel.setCollectionId(collection.getId());
        // 写入ES,es使用的是esIndex
        var esIndex = indexModel.getName() + "@" + System.currentTimeMillis();
        indexModel.setEsIndexName(esIndex);
        elasticOperator.createIndex(esIndex);
        // 写入数据库
        var newIndex = new Index();
        newIndex.setName(indexModel.getName());
        newIndex.setDesc(indexModel.getDesc());
        newIndex.setEsIndexName(esIndex);
        newIndex.setStatus(IndexStatus.Inactivated);
        newIndex.setCollectionId(indexModel.getCollectionId());
        newIndex.setCollectionName(indexModel.getCollectionName());
        newIndex.setAutoAppendFromCollection(indexModel.getAutoAppendFromCollection());
        indexDao.insert(newIndex);
        insertProperties(indexModel, newIndex);
        indexModel.setId(newIndex.getId());
        indexModel.setStatus(IndexStatus.Inactivated.name());
    }

    private void checkIndexProperties(IndexModel indexModel) {
        var propertiesNameList = indexModel.getMapping().getProperties().stream().map(PropertyModel::getName).toList();
        var distinctPropertiesNameList = propertiesNameList.stream().distinct();
        if (propertiesNameList.size() != distinctPropertiesNameList.count()) {
            throw new RuntimeException("Property name can not be duplicate");
        }
        AtomicBoolean hasIdPart = new AtomicBoolean(false);
        indexModel.getMapping().getProperties().forEach(propertyModel -> {
            if (propertyModel.getDocIdPart()) {
                hasIdPart.set(true);
            }
        });
        if (!hasIdPart.get()) {
            throw new RuntimeException("Index doc id part is not exists, you need to set one property as doc id part.");
        }
    }

    private void insertProperties(IndexModel indexModel, Index index) {
        assert index.getId() != null;
        indexPropertyDao.insert(indexModel.getMapping().getProperties().stream().map(property -> dataMapper.toIndexProperty(property)
                .setIndexId(index.getId())).toList());
    }

    @Transactional
    @SneakyThrows
    public Optional<Job> updateIndex(IndexModel indexModel) {
        var index = mustGetIndex(indexModel.getName());
        assert index.getId() != null;
        index.setDesc(indexModel.getDesc());
        indexDao.update(index);
        if (indexModel.getMapping() == null) {
            return Optional.empty();
        }
        // 更新mapping
        checkIndexProperties(indexModel);
        var oldProperties = indexPropertyDao.fetchByIndexId(index.getId());
        var oldPropertiesMap = oldProperties.stream().collect(Collectors.toMap(IndexProperty::getName, it -> it));
        var updateProperties = new ArrayList<IndexProperty>();
        var insertProperties = new ArrayList<IndexProperty>();
        // 如果不兼容则需要recreate
        // 1. 新增需要recreate，这会导致es中的mapping需要新增，历史数据丢失字段
        // 2. docIdPart改变需要recreate，这会导致es历史数据中的_id不对
        // 3. type改变需要recreate，这会导致es中的mapping不兼容性的改变
        // 4. 缩减的是require的字段或者是docIdPart需要recreate
        var needRecreate = false;

        for (PropertyModel propertyModel : indexModel.getMapping().getProperties()) {
            if (propertyModel.getEnable()) {
                var old = oldPropertiesMap.remove(propertyModel.getName());
                if (old == null) {
                    // 如果新增的则一定要recreate
                    needRecreate = true;
                    insertProperties.add(
                            dataMapper.toIndexProperty(propertyModel)
                                    .setIndexId(index.getId()));
                } else {
                    if (old.getDocIdPart() != propertyModel.getDocIdPart() || !old.getType().equals(propertyModel.getType().name())) {
                        // 如果docIdPart改变或者type改变了则需要recreate
                        needRecreate = true;
                    }
                    // 更新mapping字段，因为被移出了所以重新放到更新队列中
                    updateProperties.add(dataMapper.toIndexProperty(propertyModel).setId(old.getId())
                            .setIndexId(old.getIndexId()));
                }
            } else {
                // 更新下字段
                var old = oldPropertiesMap.get(propertyModel.getName());
                old.setType(propertyModel.getType().name());
                old.setDocIdPart(propertyModel.getDocIdPart());
                old.setAlias(propertyModel.getAlias());
                old.setRequired(propertyModel.getRequired());
                old.setRestrict(objectMapper.writeValueAsString(propertyModel.getRestrict()));
                old.setEnable(propertyModel.getEnable());
            }
        }
        // 剩下的是缩减的字段
        for (IndexProperty indexProperty : oldPropertiesMap.values()) {
            indexProperty.setEnable(false);
            updateProperties.add(indexProperty);
            if (Boolean.TRUE.equals(indexProperty.getDocIdPart()) || Boolean.TRUE.equals(indexProperty.getRequired())) {
                needRecreate = true;
                break;
            }
        }

        assert index.getStatus() != null;
        if (!index.getStatus().equals(IndexStatus.Inactivated) && needRecreate) {
            // 这种情况需要recreate就直接删干净重来
            indexPropertyDao.ctx().deleteFrom(INDEX_PROPERTY)
                    .where(INDEX_PROPERTY.INDEX_ID.eq(index.getId()))
                    .execute();
            insertProperties(indexModel, index);
            return Optional.of(recreateIndex(indexModel.getName()));
        } else {
            // 否则就更新mapping字段
            var updateList = updateProperties.stream().filter(it -> {
                if (it.getEnable() != null) {
                    return it.getEnable();
                } else {
                    return false;
                }
            }).toList();
            if (!updateList.isEmpty()) {
                indexPropertyDao.update(updateList);
            }
            var deleteList = updateProperties.stream().filter(it -> {
                if (it.getEnable() != null) {
                    return !it.getEnable();
                } else {
                    return true;
                }
            }).toList();
            if (!deleteList.isEmpty()) {
                indexPropertyDao.delete(deleteList);
            }
            if (!insertProperties.isEmpty()) {
                indexPropertyDao.insert(insertProperties);
            }
            return Optional.empty();
        }
    }

    @SneakyThrows
    public DocumentModel getDocument(String indexName, String docId) {
        var esIndexName = getEsIndexName(indexName);
        var elasticDocument = elasticOperator.getDocument(esIndexName, docId);
        var doc = new DocumentModel();
        doc.setIndex(indexName);
        doc.setSource(objectMapper.readValue(elasticDocument.getDocumentJson(), Object.class));
        doc.setId(docId);
        return doc;
    }

    /**
     * 可以修改也可以新增
     */
    public void updateDocument(String indexName, String docId, Object document) throws JsonProcessingException {
        var indexModel = mustGetIndexModel(indexName);
        if (indexModel.getStatus().equals(IndexStatus.Migrating.name())) {
            throw new RuntimeException("Index is migrating, can not insert or update document");
        }
        // 获取ES上的文档
        var elasticDoc = elasticOperator.getDocument(indexModel.getEsIndexName(), docId);
        // 获取数据库中原始文档
        var docOriginalSource = docDao.fetchOptionalById(elasticDoc.getSourceId()).orElseThrow(() -> new RuntimeException("Doc not found")).getSource();
        // 验证做出的修改是否合法
        var documentUpdateSource = objectMapper.writeValueAsString(document);
        var source = verifyDocService.verifyDoc(indexModel, documentUpdateSource);
        // 合并修改到数据库中原始的文档中，确保字段不会丢失
        var docOriginalObjectNode = objectMapper.convertValue(objectMapper.readTree(docOriginalSource), ObjectNode.class);
        objectMapper.readTree(documentUpdateSource).fields().forEachRemaining(entry -> {
            docOriginalObjectNode.replace(entry.getKey(), entry.getValue());
        });
        var modifySource = objectMapper.writeValueAsString(docOriginalObjectNode);
        // 插入数据库，对于doc表来说不存在更新的操作，只有插入
        var newDoc = new Doc();
        newDoc.setSource(modifySource);
        newDoc.setCollectionId(indexModel.getId());
        newDoc.setModifyByIndex(indexModel.getId());
        docDao.insert(newDoc);
        // 将转换后的文档更新或者插入ES
        elasticOperator.createDocument(ElasticDocument.builder().index(indexModel.getEsIndexName()).id(docId).documentJson(source).build());
    }

    public List<ElasticDocument> processInsertDocument(IndexModel indexModel,
                                                       List<ElasticDocument> batchDoc,
                                                       Boolean ignoreError,
                                                       Consumer<Exception> exceptionConsumer) {
        var docIdPart = indexModel.getMapping().getProperties().stream().filter(PropertyModel::getDocIdPart).map(PropertyModel::getName).sorted().toList();
        if (docIdPart.isEmpty()) {
            throw new RuntimeException("Index doc id part is not exists, you need to set one property as doc id part.");
        }
        var verifyBatchDoc = new ArrayList<ElasticDocument>();
        batchDoc.forEach(it -> {
            var edBuilder = ElasticDocument.builder();
            // 验证并转换
            try {
                edBuilder.documentJson(verifyDocService.verifyDoc(indexModel, it.getDocumentJson()));
                var id = docIdPart.stream().map(propertyName -> {
                    var value = "";
                    try {
                        var sourceJsonObject = objectMapper.readTree(it.getDocumentJson());
                        var valueNode = sourceJsonObject.get(propertyName);
                        if (valueNode != null) {
                            value = valueNode.asText();
                        }
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    return value;
                }).collect(Collectors.joining("-"));
                edBuilder.id(id);
                edBuilder.index(indexModel.getEsIndexName());
                edBuilder.sourceId(it.getSourceId());
            } catch (Exception e) {
                if (!ignoreError) {
                    throw e;
                } else {
                    exceptionConsumer.accept(e);
                }
            }
            verifyBatchDoc.add(edBuilder.build());
        });
        return verifyBatchDoc;
    }

    public List<DocumentModel> searchDocument(String indexName, String body) {
        var index = mustGetIndex(indexName);
        var elasticDocuments = elasticOperator.searchDocument(index.getEsIndexName(), body);
        return elasticDocuments.stream().map(elasticDocument -> {
            var doc = new DocumentModel();
            doc.setIndex(indexName);
            doc.setId(elasticDocument.getId());
            try {
                doc.setSource(objectMapper.readValue(elasticDocument.getDocumentJson(), Object.class));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return doc;
        }).collect(Collectors.toList());
    }

    public Job recreateIndex(String indexName) {
        var indexModel = mustGetIndexModel(indexName);
        if (indexModel.getStatus().equals(IndexStatus.Migrating.name())) {
            throw new RuntimeException("Index is migrating, can not recreate");
        }
        var oldEsIndexName = indexModel.getEsIndexName();
        var newEsIndexName = indexName + "@" + System.currentTimeMillis();
        elasticOperator.createIndex(newEsIndexName);
        indexModel.setEsIndexName(newEsIndexName);
        // 有可能为null因为可能被主动删了
        if (oldEsIndexName != null) {
            elasticOperator.deleteIndex(oldEsIndexName);
        }
        var index = indexDao.fetchOne(INDEX.NAME, indexModel.getName());
        assert index != null;
        // 重新导入数据(异步方式)
        var job = jobService.createReinsertIndexDocJob(index.getId());
        index.setEsIndexName(newEsIndexName);
        index.setStatus(IndexStatus.Migrating);
        indexDao.update(index);
        return job;
    }

    @SneakyThrows
    public Job activeIndex(String indexName) {
        var indexModel = mustGetIndexModel(indexName);
        if (indexModel.getStatus().equals(IndexStatus.Inactivated.name())) {
            indexDao.ctx().update(INDEX)
                    .set(INDEX.STATUS, IndexStatus.Migrating)
                    .where(INDEX.NAME.eq(indexName))
                    .execute();
        } else {
            throw new RuntimeException("Index is not inactivated, can not active");
        }
        // 固化es的mapping
        var mappingNode = objectMapper.createObjectNode();
        var propertiesNode = objectMapper.createObjectNode();
        indexModel.getMapping().getProperties().forEach(propertyModel -> {
            switch (propertyModel.getType()) {
                case TEXT -> propertiesNode.put(propertyModel.getName(), "keyword");
                case NUMBER -> propertiesNode.put(propertyModel.getName(), "numeric");
                case BOOL -> propertiesNode.put(propertyModel.getName(), "boolean");
                case DATE -> propertiesNode.put(propertyModel.getName(), "date");
            }
        });
        mappingNode.set("properties", objectMapper.createObjectNode());
        elasticOperator.createMapping(indexModel.getEsIndexName(), objectMapper.writeValueAsString(mappingNode));
        // 开始导入数据的任务
        return jobService.createReinsertIndexDocJob(indexModel.getId());
    }

    /**
     * 追加数据
     */
    public Job appendData(String indexName) {
        var index = mustGetIndex(indexName);
        assert index.getStatus() != null;
        if (index.getStatus().equals(IndexStatus.Inactivated)) {
            throw new RuntimeException("Index is inactivated, can not append data");
        }
        if (index.getStatus().equals(IndexStatus.Migrating)) {
            throw new RuntimeException("Index is migrating, can not append data");
        }
        var job = jobService.createAppendIndexDocJob(index.getId());
        index.setStatus(IndexStatus.Migrating);
        indexDao.update(index);
        return job;
    }

    /**
     * 追加数据的具体实现
     */
    public void appendDataProcess(String indexName, Long startDocId, Consumer<Exception> exceptionConsumer) {
        var startDocIdAtomic = new AtomicReference<>(startDocId);
        try {
            final int pageSize = 10; // 每页的大小
            boolean hasNextPage = true;
            var indexModel = mustGetIndexModel(indexName);
            while (hasNextPage) {
                // 构建分页查询并执行
                var docs = docDao.ctx().select().from(Tables.DOC)
                        .where(Tables.DOC.ID.gt(startDocIdAtomic.get())
                                .and(Tables.DOC.MODIFY_BY_INDEX.eq(indexModel.getId())
                                        .or(Tables.DOC.MODIFY_BY_INDEX.isNull())))
                        .limit(pageSize)
                        .fetchInto(Doc.class);
                if (docs.isEmpty()) {
                    break;
                }
                docs.stream().map(Doc::getId).filter(Objects::nonNull).max(Long::compareTo).ifPresent(startDocIdAtomic::set);
                var batchDoc = processInsertDocument(indexModel, docs.stream().map(it -> ElasticDocument.builder().index(indexModel.getEsIndexName())
                        .sourceId(it.getId())
                        .documentJson(it.getSource()).build()).toList(), true, exceptionConsumer);
                elasticOperator.batchInsertDocument(batchDoc);
                // 检查是否有下一页
                hasNextPage = docs.size() == pageSize;
            }
        } catch (Exception e) {
            log.error("appendDataProcess error", e);
        } finally {
            // 恢复索引状态
            var index = indexDao.fetchOne(INDEX.NAME, indexName);
            assert index != null;
            index.setStatus(IndexStatus.Activated);
            indexDao.update(index);
            // 记录最后的docId到IndexDocRecord表
            var indexDocRecord = indexDocRecordDao.fetchOptional(INDEX_DOC_RECORD.INDEX_ID, index.getId());
            if (indexDocRecord.isPresent()) {
                var nowIndexDocRecord = indexDocRecord.get();
                nowIndexDocRecord.setLatestDocId(startDocIdAtomic.get());
                indexDocRecordDao.update(nowIndexDocRecord);
            } else {
                var newIndexDocRecord = new IndexDocRecord();
                assert index.getId() != null;
                newIndexDocRecord.setIndexId(index.getId());
                newIndexDocRecord.setLatestDocId(startDocIdAtomic.get());
                indexDocRecordDao.insert(newIndexDocRecord);
            }
        }
    }
}
