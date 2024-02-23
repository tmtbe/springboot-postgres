package com.thoughtworks.projectDemo.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.model.DocumentModel;
import com.thoughtworks.projectDemo.model.IndexModel;
import com.thoughtworks.projectDemo.service.IndexService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class Index implements IndexApiDelegate {
    private final IndexService indexService;
    private final ObjectMapper objectMapper;

    /**
     * DELETE /indices/{indexName} : 删除索引
     *
     * @param indexName  (required)
     * @param retainData 是否保留index对doc的修改 (optional)
     * @return OK (status code 200)
     * @see IndexApi#deleteIndicesIndexName
     */
    @Override
    public ResponseEntity<Void> deleteIndicesIndexName(String indexName, Optional<Boolean> retainData) throws Exception {
        indexService.deleteIndex(indexName, retainData.orElse(false));
        return ResponseEntity.ok().build();
    }


    /**
     * GET /indices/{indexName} : 获取index
     *
     * @param indexName (required)
     * @return OK (status code 200)
     * @see IndexApi#getIndicesIndexName
     */
    @Override
    public ResponseEntity<IndexModel> getIndicesIndexName(String indexName) throws Exception {
        var index = indexService.mustGetIndexModel(indexName);
        return ResponseEntity.ok(index);
    }

    /**
     * GET /indices/{indexName}/doc/{docId} : 获取文档
     *
     * @param indexName (required)
     * @param docId     (required)
     * @return OK (status code 200)
     * @see IndexApi#getIndicesIndexNameDoc
     */
    @Override
    public ResponseEntity<DocumentModel> getIndicesIndexNameDoc(String indexName, String docId) throws Exception {
        var document = indexService.getDocument(indexName, docId);
        return ResponseEntity.ok(document);
    }

    /**
     * GET /indices/{indexName}/search : 查询文档
     *
     * @param indexName (required)
     * @param body      (optional)
     * @return OK (status code 200)
     * @see IndexApi#getIndicesIndexNameSearch
     */
    @Override
    public ResponseEntity<List<DocumentModel>> getIndicesIndexNameSearch(String indexName, Object body) throws Exception {
        var documents = indexService.searchDocument(indexName, objectMapper.writeValueAsString(body));
        return ResponseEntity.ok(documents);
    }

    /**
     * POST /indices/{indexName} : 更新index
     *
     * @param indexName  (required)
     * @param indexModel (optional)
     * @return OK (status code 200)
     * @see IndexApi#postIndicesIndexName
     */
    @Override
    public ResponseEntity<IndexModel> postIndicesIndexName(String indexName, IndexModel indexModel) throws Exception {
        indexModel.setName(indexName);
        var optionalJob = indexService.updateIndex(indexModel);
        if (optionalJob.isPresent()) {
            var job = optionalJob.get();
            var headers = new HttpHeaders();
            assert job.getId() != null;
            headers.add("jobId", job.getId().toString());
            return new ResponseEntity<>(indexService.mustGetIndexModel(indexName), headers, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(indexService.mustGetIndexModel(indexName), HttpStatus.OK);
        }
    }

    /**
     * POST /indices/{indexName}/active : 激活Index，开始导入数据，激活后无法修改索引的mapping
     *
     * @param indexName (required)
     * @return OK (status code 200)
     * @see IndexApi#postIndicesIndexNameActive
     */
    @Override
    public ResponseEntity<IndexModel> postIndicesIndexNameActive(String indexName) throws Exception {
        var job = indexService.activeIndex(indexName);
        var headers = new HttpHeaders();
        assert job.getId() != null;
        headers.add("jobId", job.getId().toString());
        return new ResponseEntity<>(indexService.mustGetIndexModel(indexName), headers, HttpStatus.OK);
    }

    /**
     * POST /indices/{indexName}/append : 刷新并追加Index的数据
     *
     * @param indexName (required)
     * @return OK (status code 200)
     * @see IndexApi#postIndicesIndexNameAppend
     */
    @Override
    public ResponseEntity<IndexModel> postIndicesIndexNameAppend(String indexName) throws Exception {
        var job = indexService.appendData(indexName);
        var headers = new HttpHeaders();
        assert job.getId() != null;
        headers.add("jobId", job.getId().toString());
        return new ResponseEntity<>(indexService.mustGetIndexModel(indexName), headers, HttpStatus.OK);
    }

    /**
     * PUT /indices/{indexName} : 添加index
     *
     * @param indexName  (required)
     * @param indexModel (optional)
     * @return OK (status code 200)
     * @see IndexApi#putIndicesIndexName
     */
    @Override
    public ResponseEntity<IndexModel> putIndicesIndexName(String indexName, IndexModel indexModel) throws Exception {
        indexModel.setName(indexName);
        indexService.createIndex(indexModel);
        return new ResponseEntity<>(indexModel, HttpStatus.OK);
    }

    /**
     * PUT /indices/{indexName}/doc/{docId} : 更新文档
     *
     * @param indexName (required)
     * @param docId     (required)
     * @param body      (optional)
     * @return OK (status code 200)
     * @see IndexApi#putIndicesIndexNameDocDocId
     */
    @Override
    public ResponseEntity<Void> putIndicesIndexNameDocDocId(String indexName, String docId, Object body) throws Exception {
        indexService.updateDocument(indexName, docId, body);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
