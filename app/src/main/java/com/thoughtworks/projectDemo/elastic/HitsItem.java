package com.thoughtworks.projectDemo.elastic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HitsItem{

	@JsonProperty("_index")
	private String index;

	@JsonProperty("_source")
	private Object source;

	@JsonProperty("_id")
	private String id;

	@JsonProperty("_score")
	private Object score;
}