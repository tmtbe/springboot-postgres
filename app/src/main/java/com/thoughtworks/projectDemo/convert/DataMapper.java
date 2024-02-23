package com.thoughtworks.projectDemo.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.amqp.AmqpJob;
import com.thoughtworks.projectDemo.model.IndexModel;
import com.thoughtworks.projectDemo.model.PropertyModel;
import com.thoughtworks.projectDemo.model.RestrictModel;
import com.thoughtworks.projectDemo.tables.pojos.Index;
import com.thoughtworks.projectDemo.tables.pojos.IndexProperty;
import com.thoughtworks.projectDemo.tables.pojos.Job;
import lombok.SneakyThrows;
import org.jooq.JSONB;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "Spring")
public interface DataMapper {
      @SneakyThrows
      @Named("toJson")
      public static String toJson(Object obj) {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(obj);
      }

      @SneakyThrows
      @Named("toRestrictModel")
      public static RestrictModel toRestrictModel(String json) {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, RestrictModel.class);
      }

      @Named("jsonbToString")
      public static String jsonbToString(JSONB jsonb) {
            return jsonb.data();
      }

      @Mapping(target = "mapping", ignore = true)
      IndexModel toIndexModel(Index index);

      @Mapping(target = "restrict", source = "restrict", qualifiedByName = "toRestrictModel")
      PropertyModel toPropertyModel(IndexProperty indexProperty);

      @Mapping(target = "updateAt", ignore = true)
      @Mapping(target = "indexId", ignore = true)
      @Mapping(target = "id", ignore = true)
      @Mapping(target = "createAt", ignore = true)
      @Mapping(target = "restrict", source = "restrict", qualifiedByName = "toJson")
      IndexProperty toIndexProperty(PropertyModel indexProperty);

      @Mapping(target = "jobData", source = "jobData", qualifiedByName = "jsonbToString")
      AmqpJob toAmqpJob(Job job);
}
