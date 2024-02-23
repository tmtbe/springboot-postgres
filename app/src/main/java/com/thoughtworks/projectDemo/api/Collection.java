package com.thoughtworks.projectDemo.api;

import com.thoughtworks.projectDemo.model.CollectionModel;
import com.thoughtworks.projectDemo.model.PropertyModel;
import com.thoughtworks.projectDemo.service.CollectionService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class Collection implements CollectionApiDelegate {
    private final CollectionService collectionService;

    /**
     * DELETE /collections/{collectionName} : 删除Collection
     *
     * @param collectionName (required)
     * @return OK (status code 200)
     * @see CollectionApi#deleteCollectionsCollectionName
     */
    @Override
    public ResponseEntity<Void> deleteCollectionsCollectionName(String collectionName) throws Exception {
        collectionService.deleteCollection(collectionName);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /collections : 获取所有的数据集合
     *
     * @return OK (status code 200)
     * @see CollectionApi#getCollections
     */
    @Override
    public ResponseEntity<List<CollectionModel>> getCollections() throws Exception {
        var collections = collectionService.getCollections();
        return ResponseEntity.ok(collections);
    }

    /**
     * GET /collections/{collectionName} : 获取Collection信息
     *
     * @param collectionName (required)
     * @return OK (status code 200)
     * @see CollectionApi#getCollectionsCollectionName
     */
    @Override
    public ResponseEntity<CollectionModel> getCollectionsCollectionName(String collectionName) throws Exception {
        var collection = collectionService.getCollection(collectionName);
        return ResponseEntity.ok(collection);
    }

    /**
     * GET /collections/{collectionName}/properties : 获取集合中记录所有的Property
     *
     * @param collectionName (required)
     * @return OK (status code 200)
     * @see CollectionApi#getCollectionsCollectionNameProperties
     */
    @Override
    public ResponseEntity<List<PropertyModel>> getCollectionsCollectionNameProperties(String collectionName) throws Exception {
        var properties = collectionService.getProperties(collectionName);
        return ResponseEntity.ok(properties);
    }

    /**
     * POST /collections/{collectionName}/upload : 上传excel文件
     *
     * @param collectionName (required)
     * @param file           (optional)
     * @return OK (status code 200)
     * @see CollectionApi#postCollectionsCollectionNameUpload
     */
    @Override
    public ResponseEntity<Void> postCollectionsCollectionNameUpload(String collectionName, MultipartFile file) throws Exception {
        collectionService.uploadExcel(collectionName, file.getInputStream());
        return ResponseEntity.ok().build();
    }


    /**
     * POST /collections/{collectionName}/docs : 批量上传文档
     *
     * @param collectionName (required)
     * @param batchId        (optional)
     * @param requestBody    (optional)
     * @return OK              (status code 200)
     * @see CollectionApi#postCollectionsDocs
     */
    @Override
    public ResponseEntity<Void> postCollectionsDocs(String collectionName, Optional<String> batchId, List<Object> requestBody) throws Exception {
        collectionService.batchUpload(collectionName, batchId, requestBody);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /collections/{collectionName} : 创建Collection
     *
     * @param collectionName  (required)
     * @param collectionModel (optional)
     * @return OK (status code 200)
     * @see CollectionApi#putCollectionsCollectionName
     */
    @Override
    public ResponseEntity<CollectionModel> putCollectionsCollectionName(String collectionName, CollectionModel collectionModel) throws Exception {
        collectionModel.setName(collectionName);
        collectionService.createCollection(collectionModel);
        var collection = collectionService.getCollection(collectionName);
        return ResponseEntity.ok(collection);
    }

}
