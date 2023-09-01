package com.thoughtworks.projectDemo.service;

import com.thoughtworks.projectDemo.api.OpenapiApiDelegate;
import com.thoughtworks.projectDemo.convert.DataMapper;
import com.thoughtworks.projectDemo.model.OpenApi;
import com.thoughtworks.projectDemo.tables.daos.OpenApiDao;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class OpenApiService implements OpenapiApiDelegate {
    private final OpenApiDao openApiDao;
    private final DataMapper dataMapper;

    @Override
    public ResponseEntity<OpenApi> getOpenapiOpenapiId(Long openapiId) throws Exception {
        return openApiDao.fetchOptionalById(openapiId)
                .map(dataMapper::toEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
