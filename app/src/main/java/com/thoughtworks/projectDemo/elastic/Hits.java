package com.thoughtworks.projectDemo.elastic;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Hits{

	@JsonProperty("hits")
	private List<HitsItem> hits;

	@JsonProperty("total")
	private Total total;

	@JsonProperty("max_score")
	private Object maxScore;
}