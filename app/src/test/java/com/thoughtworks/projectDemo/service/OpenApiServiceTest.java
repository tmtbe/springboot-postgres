package com.thoughtworks.projectDemo.service;

import com.thoughtworks.projectDemo.model.OpenApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

@SpringBootTest
public class OpenApiServiceTest {
    @Autowired
    private OpenApiService openApiService;
    @Test
    public void test_getOpenapiOpenapiId() throws Exception {
        ResponseEntity<OpenApi> openapiOpenapiId = openApiService.getOpenapiOpenapiId(1L);
        assert openapiOpenapiId.getStatusCode() == HttpStatusCode.valueOf(404);
    }
}
