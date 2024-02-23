package com.thoughtworks.projectDemo.elastic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Shards {

    @JsonProperty("total")
    private int total;

    @JsonProperty("failed")
    private int failed;

    @JsonProperty("successful")
    private int successful;

    @JsonProperty("skipped")
    private int skipped;
}