package com.thoughtworks.projectDemo.excel;


import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.enums.ReadDefaultReturnEnum;
import com.alibaba.excel.metadata.data.DataFormatData;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.read.listener.IgnoreExceptionReadListener;
import com.alibaba.excel.read.metadata.holder.ReadSheetHolder;
import com.alibaba.excel.util.ConverterUtils;
import com.alibaba.excel.util.DateUtils;
import com.alibaba.excel.util.ListUtils;
import com.thoughtworks.projectDemo.model.PropertyModel;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Convert to the object the user needs
 *
 * @author jipengfei
 */
public class ModelBuildEventListener implements IgnoreExceptionReadListener<Map<Integer, ReadCellData<?>>> {
    private final Integer batchCount;
    private final Consumer<List<Object>> batchReadConsumer;
    private final Consumer<Void> finishReadConsumer;
    private final Consumer<Map<String, Set<PropertyModel.TypeEnum>>> propertyMapConsumer;
    private final Map<String, Set<PropertyModel.TypeEnum>> propertyMap = new HashMap<>();
    private final Map<Integer, String> headerMap = new HashMap<>();
    private List<Object> cachedDataList;

    public ModelBuildEventListener(Integer batchCount,
                                   Consumer<Map<String, Set<PropertyModel.TypeEnum>>> propertyMapConsumer,
                                   Consumer<List<Object>> batchReadConsumer,
                                   Consumer<Void> finishReadConsumer) {
        this.batchCount = batchCount;
        this.batchReadConsumer = batchReadConsumer;
        this.finishReadConsumer = finishReadConsumer;
        this.propertyMapConsumer = propertyMapConsumer;
        this.cachedDataList = ListUtils.newArrayListWithExpectedSize(batchCount);
    }

    @Override
    public void invoke(Map<Integer, ReadCellData<?>> cellDataMap, AnalysisContext context) {
        ReadSheetHolder readSheetHolder = context.readSheetHolder();
        context.readRowHolder().setCurrentRowAnalysisResult(buildNoModel(cellDataMap, readSheetHolder, context));
    }

    @Override
    public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
        headMap.forEach((k, v) -> {
            headerMap.put(k, headMap.get(k).getStringValue());
        });
    }

    private void recordProperty(String key, ReadCellData<?> cellData) {
        Set<PropertyModel.TypeEnum> set = propertyMap.computeIfAbsent(key, k -> new HashSet<>());
        switch (cellData.getType()) {
            case STRING:
            case DIRECT_STRING:
            case ERROR:
            case EMPTY:
                set.add(PropertyModel.TypeEnum.TEXT);
                break;
            case BOOLEAN:
                set.add(PropertyModel.TypeEnum.BOOL);
                break;
            case NUMBER:
                DataFormatData dataFormatData = cellData.getDataFormatData();
                if (dataFormatData != null && DateUtils.isADateFormat(dataFormatData.getIndex(),
                        dataFormatData.getFormat())) {
                    set.add(PropertyModel.TypeEnum.DATE);
                } else {
                    set.add(PropertyModel.TypeEnum.NUMBER);
                }
                break;
            case DATE:
                set.add(PropertyModel.TypeEnum.DATE);
                break;
            default:
                break;
        }
    }

    private Object buildNoModel(Map<Integer, ReadCellData<?>> cellDataMap, ReadSheetHolder readSheetHolder,
                                AnalysisContext context) {
        Map<String, Object> map = new HashMap<>();
        headerMap.forEach((k, v) -> {
            map.put(v, null);
        });
        for (Map.Entry<Integer, ReadCellData<?>> entry : cellDataMap.entrySet()) {
            Integer key = entry.getKey();
            ReadCellData<?> cellData = entry.getValue();
            recordProperty(headerMap.get(key), cellData);
            ReadCellData<?> convertedReadCellData = convertReadCellData(cellData,
                    context.readWorkbookHolder().getReadDefaultReturn(), readSheetHolder, context, key);
            map.put(headerMap.get(key), convertedReadCellData.getData());
        }
        cachedDataList.add(map);
        if (cachedDataList.size() >= batchCount) {
            batchReadConsumer.accept(cachedDataList);
            cachedDataList = ListUtils.newArrayListWithExpectedSize(batchCount);
        }
        return map;
    }

    private ReadCellData convertReadCellData(ReadCellData<?> cellData, ReadDefaultReturnEnum readDefaultReturn,
                                             ReadSheetHolder readSheetHolder, AnalysisContext context, Integer columnIndex) {
        Class<?> classGeneric;
        switch (cellData.getType()) {
            case STRING:
            case DIRECT_STRING:
            case ERROR:
            case EMPTY:
                classGeneric = String.class;
                break;
            case BOOLEAN:
                classGeneric = Boolean.class;
                break;
            case NUMBER:
                DataFormatData dataFormatData = cellData.getDataFormatData();
                if (dataFormatData != null && DateUtils.isADateFormat(dataFormatData.getIndex(),
                        dataFormatData.getFormat())) {
                    classGeneric = LocalDateTime.class;
                } else {
                    classGeneric = Long.class;
                }
                break;
            default:
                classGeneric = ConverterUtils.defaultClassGeneric;
                break;
        }

        return (ReadCellData) ConverterUtils.convertToJavaObject(cellData, null, ReadCellData.class,
                classGeneric, null, readSheetHolder.converterMap(), context, context.readRowHolder().getRowIndex(),
                columnIndex);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (!cachedDataList.isEmpty()) {
            batchReadConsumer.accept(cachedDataList);
        }
        finishReadConsumer.accept(null);
        propertyMapConsumer.accept(propertyMap);
    }
}
