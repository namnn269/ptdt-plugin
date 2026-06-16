package com.fds.flexdata.plugin.ptdt.domain.handler_advice.do_thi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.fds.flexdata.plugin.ptdt.domain.service.DataPermissionService;
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
public class UpdateDoThiHandlerAdvice implements HandlerAdvice, ExtensionPoint {

    private final HandlerAdviceKey key;
    private final DataPermissionService dataPermissionService;

    public UpdateDoThiHandlerAdvice(Environment env, DataPermissionService dataPermissionService) {
        this.dataPermissionService = dataPermissionService;
        String csdl = env.getProperty("app.datasource.namespace.ptdt", "csdl-ptdt");
        this.key = new HandlerAdviceKey(csdl, "T_DoThi", OpenAPI.Type.UPDATE);
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
        checkPermission(body);
    }

    private void checkPermission(JsonNode body) {
        dataPermissionService.checkTinhThanh(body.path("TrucThuocTinhThanh").path("MaMuc").asString());

        JsonNode diaBans = body.path("DiaBanTrucThuoc");

        if (!diaBans.isArray()) {
            return;
        }

        for (JsonNode diaBan : diaBans) {
            checkDiaBan(diaBan);
        }
    }

    private void checkDiaBan(JsonNode diaBan) {
        dataPermissionService.checkTinhThanh(diaBan.path("TinhThanh").path("MaMuc").asString());
        dataPermissionService.checkXaPhuong(diaBan.path("XaPhuong").path("MaMuc").asString());
    }
}