package com.fds.flexdata.plugin.ptdt.application.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Getter
@Setter
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class DocumentCounterResponse {
    private String collectionName;
    private String alias;
    private long documentQuantity;
}
