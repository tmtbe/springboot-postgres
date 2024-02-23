package com.thoughtworks.projectDemo.elastic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class Hits {

	@JsonProperty("hits")
	private List<HitsItem> hits;

	@JsonProperty("total")
	private Total total;

	@JsonProperty("max_score")
	private Object maxScore;
}