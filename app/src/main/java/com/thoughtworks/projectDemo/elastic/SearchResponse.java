package com.thoughtworks.projectDemo.elastic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SearchResponse{

	@JsonProperty("_shards")
	private Shards shards;

	@JsonProperty("hits")
	private Hits hits;

	@JsonProperty("took")
	private int took;

	@JsonProperty("timed_out")
	private boolean timedOut;
}