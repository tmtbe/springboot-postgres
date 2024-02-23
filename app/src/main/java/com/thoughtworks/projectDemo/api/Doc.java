package com.thoughtworks.projectDemo.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.elastic.ElasticDocument;
import com.thoughtworks.projectDemo.model.DocumentModel;
import com.thoughtworks.projectDemo.model.IndexModel;
import com.thoughtworks.projectDemo.service.IndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class Doc implements DocApiDelegate{
    private final ObjectMapper objectMapper;
    private final IndexService indexService;


    @Override
    public ResponseEntity<Void> deleteIndicesIndexName(String indexName) throws Exception {
        indexService.deleteIndex(indexName);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<IndexModel>> getCollections() throws Exception {
        return new ResponseEntity<>(indexService.getCollections(),HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> deleteCollectionsCollectionName(String collectionName) throws Exception {
        indexService.deleteCollection(collectionName);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    public ResponseEntity<IndexModel> getCollectionsCollectionName(String collectionName) throws Exception {
        var index = indexService.mustGetCollectionModel(collectionName);
        return new ResponseEntity<>(index, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<IndexModel>> getIndices() throws Exception {
        return new ResponseEntity<>(indexService.getIndices(),HttpStatus.OK);
    }

    @Override
    public ResponseEntity<IndexModel> getIndicesIndexName(String indexName) throws Exception {
        var index = indexService.mustGetIndexModel(indexName);
        return  new ResponseEntity<>(index, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<DocumentModel> getIndicesIndexNameDoc(String indexName, String docId) throws Exception {
        var doc = indexService.getDocument(indexName, docId);
        return new ResponseEntity<>(doc,HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<DocumentModel>> getIndicesIndexNameSearch(String indexName, Object body) throws Exception {
        return new ResponseEntity<>(indexService.searchDocument(indexName,objectMapper.writeValueAsString(body)),HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> postIndicesDocs(String indexName, Optional<String> batchId, List<Object> requestBody) throws Exception {
        var batchDoc = requestBody.stream().map(object -> {
            try {
                var documentJson =  objectMapper.writeValueAsString(object);
                return ElasticDocument.builder().documentJson(documentJson)
                        .originalJson(documentJson).index(indexName).build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();
        if(batchId.isEmpty()){
            batchId = Optional.of("default");
        }
        indexService.batchInsertDocument(indexName,batchId.get(),batchDoc);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    public ResponseEntity<IndexModel> postIndicesIndexName(String indexName, IndexModel indexModel) throws Exception {
        indexModel.setName(indexName);
        indexService.updateIndex(indexModel);
        return new ResponseEntity<>(indexModel,HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> postIndicesIndexNameRecreate(String indexName) throws Exception {
        indexService.recreateIndex(indexName);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    public ResponseEntity<IndexModel> putIndicesIndexName(String indexName, IndexModel indexModel) throws Exception {
        indexModel.setName(indexName);
        indexService.createIndex(indexModel);
        return new ResponseEntity<>(indexModel,HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> putIndicesIndexNameDocDocId(String indexName, String docId, Object body) throws Exception {
        indexService.updateDocument(indexName,docId,body);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
