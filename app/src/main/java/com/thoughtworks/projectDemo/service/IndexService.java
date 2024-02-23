package com.thoughtworks.projectDemo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.elastic.ElasticDocument;
import com.thoughtworks.projectDemo.elastic.ElasticOperator;
import com.thoughtworks.projectDemo.model.*;
import com.thoughtworks.projectDemo.tables.daos.DocDao;
import com.thoughtworks.projectDemo.tables.daos.IndexDao;
import com.thoughtworks.projectDemo.tables.daos.PropertyDao;
import com.thoughtworks.projectDemo.tables.pojos.Doc;
import com.thoughtworks.projectDemo.tables.pojos.Index;
import com.thoughtworks.projectDemo.tables.pojos.Property;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static com.thoughtworks.projectDemo.Tables.DOC;
import static com.thoughtworks.projectDemo.tables.Index.INDEX;
import static com.thoughtworks.projectDemo.tables.Property.PROPERTY;

@Service
@RequiredArgsConstructor
public class IndexService {
    private final IndexDao indexDao;
    private final PropertyDao propertyDao;
    private final DocDao docDao;
    private final ObjectMapper objectMapper;
    private final ElasticOperator elasticOperator;
    private final VerifyDocService verifyDocService;


    public List<IndexModel> getCollections(){
        var indexList = indexDao.findAll();
        return buildIndexModel(indexList);
    }
    public IndexModel mustGetCollection(String collectionName){
        var index = indexDao.fetchOptional(INDEX.NAME, collectionName);
        if (index.isEmpty()){
           throw new RuntimeException("Collection is not exists");
        }
        return buildIndexModel(List.of(index.get())).get(0);
    }

    public void deleteCollection(String collectionName){
        var index = indexDao.fetchOptional(INDEX.NAME, collectionName);
        if (index.isEmpty()){
            throw new RuntimeException("Collection is not exists");
        }
        var nowIndex = index.get();
        indexDao.ctx().delete(INDEX).where(INDEX.NAME.eq(collectionName)).execute();
        indexDao.ctx().delete(PROPERTY).where(PROPERTY.INDEX_ID.eq(nowIndex.getId())).execute();
        indexDao.ctx().delete(DOC).where(DOC.INDEX_ID.eq(nowIndex.getId())).execute();
    }

    public List<IndexModel> getIndices(){
        var indexList = indexDao.ctx().selectFrom(INDEX).where(INDEX.ES_INDEX.isNotNull()).fetchInto(Index.class);
        return buildIndexModel(indexList);
    }

    private List<IndexModel> buildIndexModel(List<Index> indexList) {
        var propertyList = propertyDao.ctx().selectFrom(PROPERTY).where(PROPERTY.INDEX_ID.in(indexList.stream().map(Index::getId).toList())).fetchInto(Property.class);
        return indexList.stream().map(index -> {
            var indexModel = new IndexModel();
            indexModel.setId(index.getId());
            indexModel.setName(index.getName());
            indexModel.setDesc(index.getDesc());
            indexModel.setEsIndexName(index.getEsIndex());
            var mappingModel = new MappingModel();
            mappingModel.setProperties(propertyList.stream().filter(property -> property.getIndexId().equals(index.getId())).map(property -> {
                var propertyModel = new PropertyModel();
                propertyModel.setName(property.getName());
                propertyModel.setType(property.getType());
                propertyModel.setAlias(property.getAlias());
                propertyModel.setRequired(property.getRequired());
                propertyModel.setDocIdPart(property.getDocIdPart());
                try {
                    propertyModel.setRestrict(objectMapper.readValue(property.getRestrict(), RestrictModel.class));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                return propertyModel;
            }).toList());
            indexModel.setMapping(mappingModel);
            return indexModel;
        }).toList();
    }

    public void deleteIndex(String indexName){
        var index = indexDao.fetchOptional(INDEX.NAME, indexName);
        if (index.isEmpty()){
            throw new RuntimeException("Index is not exists");
        }
        var nowIndex = index.get();
        elasticOperator.deleteIndex(nowIndex.getEsIndex());
        nowIndex.setEsIndex(null);
        indexDao.update(nowIndex);
    }

    public IndexModel mustGetIndexModel(String indexName){
        var index = mustGetIndex(indexName);
        return buildIndexModel(List.of(index)).get(0);
    }

    private Index mustGetIndex(String indexName){
        return indexDao.ctx().selectFrom(INDEX)
                .where(INDEX.NAME.eq(indexName))
                .and(INDEX.ES_INDEX.isNotNull())
                .fetchOptional()
                .map(it->it.into(Index.class))
                .orElseThrow(()->new RuntimeException("Index is not exists"));
    }

    private String getEsIndexName(String indexName) {
        return mustGetIndex(indexName).getEsIndex();
    }

    public void createIndex(IndexModel indexModel) {
        var index = indexDao.fetchOptional(INDEX.NAME, indexModel.getName());
        if (index.isPresent()){
            throw new RuntimeException("Index already exists");
        }
        // 写入ES,es使用的是esIndex
        var esIndex = indexModel.getName()+"@"+System.currentTimeMillis();
        indexModel.setEsIndexName(esIndex);
        elasticOperator.createIndex(esIndex);
        // 写入数据库
        var newIndex = new Index();
        newIndex.setName(indexModel.getName());
        newIndex.setDesc(indexModel.getDesc());
        newIndex.setEsIndex(esIndex);
        indexDao.insert(newIndex);
        insertProperties(indexModel, newIndex);
    }

    private void insertProperties(IndexModel indexModel, Index index) {
        assert index.getId() != null;
        propertyDao.insert(indexModel.getMapping().getProperties().stream().map(property -> {
            var newProperty = new Property();
            newProperty.setIndexId(index.getId());
            newProperty.setName(property.getName());
            newProperty.setType(property.getType());
            newProperty.setAlias(property.getAlias());
            newProperty.setDocIdPart(property.getDocIdPart());
            newProperty.setRequired(property.getRequired());
            try {
                newProperty.setRestrict(objectMapper.writeValueAsString(property.getRestrict()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            return newProperty;
        }).toList());
    }

    public void updateIndex(IndexModel indexModel){
        var index = mustGetIndex(indexModel.getName());
        // 更新数据库
        index.setDesc(indexModel.getDesc());
        indexDao.update(index);
        var oldProperties = propertyDao.fetchByIndexId(index.getId());
        propertyDao.delete(oldProperties);
        insertProperties(indexModel, index);
        oldProperties.forEach(it->{
            var find = indexModel.getMapping().getProperties().stream().filter(p->p.getName().equals(it.getName())).findFirst();
            if (find.isPresent()){
                if (!find.get().getType().equals(it.getType())){
                    throw new RuntimeException("Can not change property type, you need to recreate index");
                }
            }
        });
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
        // 验证
        var original_source = objectMapper.writeValueAsString(document);
        var source = verifyDocService.verifyDoc(indexModel, original_source);
        // 原始的数据插入数据库，对于doc表来说不存在更新的操作，只有插入
        var newDoc = new Doc();
        newDoc.setSource(original_source);
        newDoc.setIndexId(indexModel.getId());
        docDao.insert(newDoc);
        // 将转换后的文档更新或者插入ES
        elasticOperator.createDocument(ElasticDocument.builder().index(indexModel.getEsIndexName()).id(docId).documentJson(source).build());
    }


    public void batchInsertDocument(String indexName,String batchId, List<ElasticDocument> batchDoc) {
        var index = mustGetIndexModel(indexName);
        var docIdPart = index.getMapping().getProperties().stream().filter(PropertyModel::getDocIdPart).map(PropertyModel::getName).sorted().toList();
        if (docIdPart.isEmpty()){
            throw new RuntimeException("Index doc id part is not exists, you need to set one property as doc id part.");
        }
        batchDoc.forEach(it-> {
            // 验证并转换
            it.setOriginalJson(it.getDocumentJson());
            it.setDocumentJson(verifyDocService.verifyDoc(index, it.getDocumentJson()));
            var id = docIdPart.stream().map(propertyName -> {
                var value = "";
                try {
                    var sourceJsonObject = objectMapper.readTree(it.getDocumentJson());
                    var valueNode = sourceJsonObject.get(propertyName);
                    if (valueNode!=null){
                        value = valueNode.asText();
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                return value;
            }).collect(Collectors.joining("-"));
            it.setId(id);
            it.setIndex(index.getEsIndexName());
        });
        docDao.insert(batchDoc.stream().map(elasticDocument -> {
            var doc = new Doc();
            doc.setIndexId(index.getId());
            doc.setBatchId(batchId);
            doc.setSource(elasticDocument.getOriginalJson());
            return doc;
        }).toList());
        // ES本来就会根据docId更新或者插入
        elasticOperator.batchInsertDocument(batchDoc);
    }

    public List<DocumentModel> searchDocument(String indexName, String body) {
        var elasticDocuments = elasticOperator.searchDocument(indexName,body);
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

    public void recreateIndex(String indexName){
        var index = mustGetIndexModel(indexName);
        var oldEsIndexName = index.getEsIndexName();
        var newEsIndexName = indexName+"@"+System.currentTimeMillis();
        elasticOperator.createIndex(newEsIndexName);
        index.setEsIndexName(newEsIndexName);
        elasticOperator.deleteIndex(oldEsIndexName);
        var indexRecord = indexDao.fetchOne(INDEX.NAME, index.getName());
        assert indexRecord != null;
        indexRecord.setEsIndex(newEsIndexName);
        indexDao.update(indexRecord);
        // todo 重新导入数据
    }
}
