package com.fds.flexdata.plugin.ptdt.domain.handler_advice.do_thi;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fds.flexdata.pluginapi.HandlerAdvice;
import com.fds.flexdata.pluginapi.annotation.OpenAPI;
import com.fds.flexdata.pluginapi.query.HandlerAdviceKey;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
//
@Component
@Extension
public class CreateDoThiHandlerAdvice implements HandlerAdvice, ExtensionPoint {

    private final HandlerAdviceKey key;

    public CreateDoThiHandlerAdvice(Environment env) {
        String csdl = env.getProperty("app.datasource.namespace.ptdt", "csdl-ptdt");
        key = new HandlerAdviceKey(csdl, "T_DoThi", OpenAPI.Type.CREATE);
    }

    @Override
    public HandlerAdviceKey getKey() {
        return key;
    }

    @Override
    public void beforeProcess(MongoDatabase database, ClientSession session, ObjectNode request) {
    }
}
