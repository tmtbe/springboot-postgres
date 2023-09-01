package com.thoughtworks.projectDemo.convert;

import com.thoughtworks.projectDemo.model.OpenApi;
import com.thoughtworks.projectDemo.tables.interfaces.IOpenApi;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "Spring")
public interface DataMapper {
    @Mapping(target = "operations", ignore = true)
    @Mapping(target = "openApi", source = "openapi")
    OpenApi toEntity(IOpenApi iOpenApi);


    @Mapping(target = "serviceName", ignore = true)
    @Mapping(target = "openapi", ignore = true)
    com.thoughtworks.projectDemo.tables.pojos.OpenApi toPojo(OpenApi openApi);
}
