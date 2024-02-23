package com.thoughtworks.projectDemo.service;

import com.alibaba.excel.EasyExcel;
import com.thoughtworks.projectDemo.excel.ModelBuildEventListener;
import com.thoughtworks.projectDemo.model.PropertyModel;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
@AllArgsConstructor
public class ExcelService {

    public void readExcel(InputStream inputStream,
                          Integer batchCount,
                          Consumer<Map<String, Set<PropertyModel.TypeEnum>>> propertyMapConsumer,
                          Consumer<List<Object>> batchReadConsumer,
                          Consumer<Void> finishReadConsumer) {
        EasyExcel.read(inputStream)
                .useDefaultListener(false)
                .sheet()
                .headRowNumber(1)
                .registerReadListener(new ModelBuildEventListener(batchCount, propertyMapConsumer, batchReadConsumer, finishReadConsumer)).doRead();
    }
}
