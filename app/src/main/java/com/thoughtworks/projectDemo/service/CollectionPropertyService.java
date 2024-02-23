package com.thoughtworks.projectDemo.service;

import com.thoughtworks.projectDemo.model.PropertyModel;
import com.thoughtworks.projectDemo.tables.daos.CollectionPropertyDao;
import com.thoughtworks.projectDemo.tables.pojos.CollectionProperty;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.thoughtworks.projectDemo.tables.CollectionProperty.COLLECTION_PROPERTY;

@Service
@AllArgsConstructor
public class CollectionPropertyService {
    private final CollectionPropertyDao collectionPropertyDao;

    @Transactional
    public void appendCollectionProperty(Long collectionId, Map<String, Set<PropertyModel.TypeEnum>> propertyMap) {
        List<CollectionProperty> collectionProperties = collectionPropertyDao.ctx().selectFrom(COLLECTION_PROPERTY)
                .where(COLLECTION_PROPERTY.COLLECTION_ID.eq(collectionId)
                        .and(COLLECTION_PROPERTY.NAME.in(propertyMap.keySet()))).fetchInto(CollectionProperty.class);
        List<CollectionProperty> needAppend = new ArrayList<>();
        for (Map.Entry<String, Set<PropertyModel.TypeEnum>> entry : propertyMap.entrySet()) {
            entry.getValue().forEach(typeEnum -> {
                if (collectionProperties.stream().noneMatch(it -> it.getName().equals(entry.getKey()) && it.getType().equals(typeEnum.name()))) {
                    needAppend.add(new CollectionProperty()
                            .setCollectionId(collectionId)
                            .setName(entry.getKey())
                            .setType(typeEnum.name()));
                }
            });
        }
        if (!needAppend.isEmpty()) {
            collectionPropertyDao.insert(needAppend);
        }
    }

    public List<PropertyModel> getProperties(Long collectionId) {
        return collectionPropertyDao.ctx().selectFrom(COLLECTION_PROPERTY)
                .where(COLLECTION_PROPERTY.COLLECTION_ID.eq(collectionId))
                .fetchInto(CollectionProperty.class)
                .stream()
                .map(it -> new PropertyModel()
                        .name(it.getName())
                        .type(PropertyModel.TypeEnum.valueOf(it.getType())))
                .toList();
    }
}
