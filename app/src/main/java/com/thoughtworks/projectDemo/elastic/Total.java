package com.thoughtworks.projectDemo.elastic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Total{

	@JsonProperty("value")
	private int value;

	@JsonProperty("relation")
	private String relation;
}