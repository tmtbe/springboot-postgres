package com.thoughtworks.projectDemo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.model.CollectionModel;
import com.thoughtworks.projectDemo.model.PropertyModel;
import com.thoughtworks.projectDemo.tables.daos.CollectionDao;
import com.thoughtworks.projectDemo.tables.daos.DocDao;
import com.thoughtworks.projectDemo.tables.pojos.Collection;
import com.thoughtworks.projectDemo.tables.pojos.Doc;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static com.thoughtworks.projectDemo.tables.Collection.COLLECTION;
import static com.thoughtworks.projectDemo.tables.Doc.DOC;

@Service
@AllArgsConstructor
public class CollectionService {
    private final CollectionDao collectionDao;
    private final DocDao docDao;
    private final CollectionPropertyService collectionPropertyService;
    private final ObjectMapper objectMapper;
    private final IndexService indexService;
    private final ExcelService excelService;

    @Transactional
    public void deleteCollection(String collectionName) {
        var collection = mustGetCollection(collectionName);
        indexService.getIndexModeList(collection.getId()).forEach(index ->
                indexService.deleteIndex(index.getName(), true));
        collectionDao.delete(collection);
        docDao.ctx().deleteFrom(DOC).where(DOC.COLLECTION_ID.eq(collection.getId())).execute();
    }

    public List<CollectionModel> getCollections() {
        return collectionDao.findAll().stream().map(collection -> new CollectionModel()
                        .id(collection.getId())
                        .name(collection.getName())
                        .desc(collection.getDesc())
                        .indices(indexService.getIndexModeList(collection.getId())))
                .toList();
    }

    public CollectionModel getCollection(String collectionName) {
        var collection = mustGetCollection(collectionName);
        return new CollectionModel()
                .id(collection.getId())
                .name(collection.getName())
                .desc(collection.getDesc())
                .indices(indexService.getIndexModeList(collection.getId()));
    }

    /**
     * 仅仅作为测试用，不包含CollectionProperty的创建
     * 正常业务通过excel上传
     */
    public void batchUpload(String collectionName, Optional<String> batchId, List<Object> batchDoc) {
        var collection = mustGetCollection(collectionName);
        if (batchDoc.isEmpty()) {
            return;
        }
        insertDoc(batchDoc, batchId.orElse("default"), collection.getId());
        notifyIndexAppend(collection.getId());
    }

    private void insertDoc(List<Object> batchDoc, String batchId, Long collectionId) {
        docDao.insert(batchDoc.stream().map(it -> {
            var doc = new Doc();
            doc.setBatchId(batchId);
            try {
                doc.setSource(objectMapper.writeValueAsString(it));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            doc.setCollectionId(collectionId);
            return doc;
        }).toList());
    }

    private void notifyIndexAppend(Long collectionId) {
        indexService.getIndices(collectionId)
                .stream().filter(indexModel -> {
                    if (indexModel.getAutoAppendFromCollection() != null) {
                        return indexModel.getAutoAppendFromCollection();
                    } else {
                        return false;
                    }
                })
                .forEach(index -> {
                    try {
                        indexService.appendData(index.getName());
                    } catch (Exception ignored) {
                    }
                });
    }

    @Transactional
    public void createCollection(CollectionModel collectionModel) {
        if (isCollectionExist(collectionModel.getName())) {
            throw new RuntimeException("Collection already exists");
        }
        var collection = new Collection();
        collection.setName(collectionModel.getName());
        collection.setDesc(collectionModel.getDesc());
        collectionDao.insert(collection);
        if (collectionModel.getIndices() != null) {
            collectionModel.getIndices().forEach(index -> {
                index.setCollectionName(collectionModel.getName());
                index.setCollectionId(collection.getId());
                indexService.createIndex(index);
            });
        }
    }

    private Collection mustGetCollection(String collectionName) {
        return collectionDao.fetchOptional(COLLECTION.NAME, collectionName)
                .orElseThrow(() -> new RuntimeException("Collection not found"));
    }

    private boolean isCollectionExist(String collectionName) {
        return collectionDao.fetchOptional(COLLECTION.NAME, collectionName).isPresent();
    }

    public void uploadExcel(String collectionName, InputStream inputStream) {
        var collection = mustGetCollection(collectionName);
        var batchId = String.valueOf(System.currentTimeMillis());
        excelService.readExcel(inputStream, 100, propertyMap -> {
            collectionPropertyService.appendCollectionProperty(collection.getId(), propertyMap);
        }, batchDoc -> {
            insertDoc(batchDoc, batchId, collection.getId());
        }, finish -> {
            notifyIndexAppend(collection.getId());
        });
    }

    public List<PropertyModel> getProperties(String collectionName) {
        var collection = mustGetCollection(collectionName);
        return collectionPropertyService.getProperties(collection.getId());
    }
}
