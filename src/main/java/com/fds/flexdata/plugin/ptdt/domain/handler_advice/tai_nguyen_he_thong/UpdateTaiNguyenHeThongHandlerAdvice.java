package com.fds.flexdata.plugin.ptdt.domain.handler_advice.tai_nguyen_he_thong;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.fds.flexdata.plugin.ptdt.service.DataPermissionService;
import com.fds.flexdata.plugin.ptdt.shared.JsonUtils;
import com.fds.flexdata.pluginapi.HandlerAdvice;
import com.fds.flexdata.pluginapi.annotation.OpenAPI;
import com.fds.flexdata.pluginapi.query.HandlerAdviceKey;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Extension
public class UpdateTaiNguyenHeThongHandlerAdvice implements HandlerAdvice, ExtensionPoint {

    private final HandlerAdviceKey key;
    private final DataPermissionService dataPermissionService;

    public UpdateTaiNguyenHeThongHandlerAdvice(Environment env, DataPermissionService dataPermissionService) {
        this.dataPermissionService = dataPermissionService;
        String csdl = env.getProperty("app.datasource.namespace.sso", "csdl-sso");
        this.key = new HandlerAdviceKey(csdl, "T_TaiNguyenHeThong", OpenAPI.Type.UPDATE);
    }

    @Override
    public HandlerAdviceKey getKey() {
        return key;
    }

    @Override
    public void beforeProcess(MongoDatabase database, ClientSession session, ObjectNode request) {
        JsonNode body = request.path("Body");
        if (JsonUtils.isEmpty(body)) {
            return;
        }
        checkPermission();
    }

    private void checkPermission() {
        dataPermissionService.checkPermission();
    }
}