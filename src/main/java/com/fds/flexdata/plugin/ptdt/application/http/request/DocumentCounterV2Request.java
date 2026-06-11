package com.fds.flexdata.plugin.ptdt.application.http.request;

import com.fds.flexdata.pluginapi.query.FilterExpression;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class DocumentCounterV2Request {

    @NotEmpty
    @Valid
    private List<DocumentFilter> collections;

    @Getter
    @Setter
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class DocumentFilter {
        @NotEmpty
        private String collectionName;
        private String alias;
        private FilterExpression filterExpression;
    }
}
