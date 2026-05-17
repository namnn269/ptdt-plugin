package com.fds.flexdata.plugin.ptdt.handler_advice;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fds.flexdata.pluginapi.HandlerAdvice;
import com.fds.flexdata.pluginapi.annotation.OpenAPI;
import com.fds.flexdata.pluginapi.query.HandlerAdviceKey;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.stereotype.Component;

@Component
@Extension
public class DoThiHandlerAdvice implements HandlerAdvice, ExtensionPoint {

    @Override
    public HandlerAdviceKey getKey() {
        return new HandlerAdviceKey("csdl-ptdt", "T_DoThi", OpenAPI.Type.CREATE);
    }

    @Override
    public void beforeProcess(MongoDatabase database, ClientSession session, ObjectNode request) {
        System.out.println("beforeProcess");
    }
}
