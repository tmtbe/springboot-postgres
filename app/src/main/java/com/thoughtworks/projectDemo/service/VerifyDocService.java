package com.thoughtworks.projectDemo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.model.IndexModel;
import com.thoughtworks.projectDemo.tables.daos.IndexDao;
import com.thoughtworks.projectDemo.tables.pojos.Index;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class VerifyDocService {
    private final ObjectMapper objectMapper;
    @SneakyThrows
    public String verifyDoc(IndexModel indexModel, String doc) {
       JsonNode jsonNode = objectMapper.readTree(doc);
       var newJsonNode = objectMapper.createObjectNode();
       if (indexModel.getMapping().getProperties().isEmpty()){
           throw new RuntimeException("No mapping found, please create mapping first");
       }
       indexModel.getMapping().getProperties().forEach(it->{
           var node = jsonNode.get(it.getName());
           if(it.getRequired()){
               if (node == null){
                   throw new RuntimeException("Field "+it.getName()+" is required");
               }
           }
           if (node!=null){
               switch (it.getType()){
                   case "text", "date":
                       newJsonNode.put(it.getName(),node.asText());
                       break;
                   case "number":
                       try{
                           var value = Long.parseLong(node.asText());
                           newJsonNode.put(it.getName(),value);
                       }catch (NumberFormatException e){
                           throw new RuntimeException("Field "+it.getName()+" type is not number");
                       }
                       break;
                   default:
                       throw new RuntimeException("Field "+it.getName()+" type is not supported");
               }
           }
       });
       return objectMapper.writeValueAsString(newJsonNode);
    }
}
