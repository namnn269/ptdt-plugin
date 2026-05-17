package com.fds.flexdata.plugin.ptdt.shared;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonUtils {
    private JsonUtils() {
    }

    public static boolean isEmpty(JsonNode jsonNode) {
        return jsonNode == null || jsonNode.isNull() || jsonNode.isMissingNode();
    }
}
