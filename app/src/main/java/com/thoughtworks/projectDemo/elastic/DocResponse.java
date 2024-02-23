package com.thoughtworks.projectDemo.elastic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DocResponse{

	@JsonProperty("_seq_no")
	private int seqNo;

	@JsonProperty("found")
	private boolean found;

	@JsonProperty("_index")
	private String index;

	@JsonProperty("_source")
	private Object source;

	@JsonProperty("_id")
	private String id;

	@JsonProperty("_version")
	private int version;

	@JsonProperty("_primary_term")
	private int primaryTerm;
}