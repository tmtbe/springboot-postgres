package com.thoughtworks.projectDemo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.model.IndexModel;
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
        if (indexModel.getMapping().getProperties().isEmpty()) {
            throw new RuntimeException("No mapping found, please create mapping first");
        }
        indexModel.getMapping().getProperties().forEach(it -> {
            var node = jsonNode.get(it.getName());
            // docIdPart 默认就是必须存在
            if (it.getRequired() || it.getDocIdPart()) {
                if (node == null) {
                    throw new RuntimeException("Field " + it.getName() + " is required");
                }
            }
            if (node != null) {
                switch (it.getType()) {
                    case BOOL:
                        if (node.isBoolean()) {
                            newJsonNode.put(it.getName(), node.asBoolean());
                        } else {
                            var text = node.asText();
                            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
                                newJsonNode.put(it.getName(), Boolean.parseBoolean(text));
                            } else {
                                throw new RuntimeException("Field " + it.getName() + " type is not boolean");
                            }
                        }
                        break;
                    case DATE:
                        // todo: check date format
                        newJsonNode.put(it.getName(), node.asText());
                        break;
                    case NUMBER:
                        try {
                            var value = Long.parseLong(node.asText());
                            newJsonNode.put(it.getName(), value);
                        } catch (NumberFormatException e) {
                            throw new RuntimeException("Field " + it.getName() + " type is not number");
                        }
                        break;
                    case TEXT:
                        newJsonNode.put(it.getName(), node.asText());
                        break;
                    default:
                        throw new RuntimeException("Field " + it.getName() + " type is not supported");
                }
            }
        });
        return objectMapper.writeValueAsString(newJsonNode);
    }
}
