package com.fds.flexdata.plugin.ptdt.domain.handler_advice.can_bo;

import tools.jackson.databind.node.ObjectNode;
import com.fds.flex.context.model.User;
import com.fds.flex.user.context.UserContextHolder;
import com.fds.flexdata.plugin.ptdt.domain.service.DataPermissionService;
import com.fds.flexdata.plugin.ptdt.shared.JsonUtils;
import com.fds.flexdata.pluginapi.HandlerAdvice;
import com.fds.flexdata.pluginapi.annotation.OpenAPI;
import com.fds.flexdata.pluginapi.exception.AppException;
import com.fds.flexdata.pluginapi.object_value.MessageCode;
import com.fds.flexdata.pluginapi.query.HandlerAdviceKey;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Extension
public class CreateCanBoHandlerAdvice implements HandlerAdvice, ExtensionPoint {

    private final HandlerAdviceKey key;
    private final DataPermissionService dataPermissionService;

    public CreateCanBoHandlerAdvice(Environment env, DataPermissionService dataPermissionService) {
        this.dataPermissionService = dataPermissionService;
        String csdl = env.getProperty("app.datasource.namespace.ptdt", "csdl-ptdt");
        this.key = new HandlerAdviceKey(csdl, "T_CanBo", OpenAPI.Type.CREATE);
    }

    @Override
    public HandlerAdviceKey getKey() {
        return key;
    }

    @Override
    public void beforeProcess(MongoDatabase database, ClientSession session, ObjectNode request) {

       User.DanhTinhDienTu danhTinhDienTu =   UserContextHolder.getContext().getUser().getDanhTinhDienTu();
        if (danhTinhDienTu == null) {
            throw new AppException(
                    MessageCode.LOI_DU_LIEU,
                    "Không tìm thấy thông tin danh tính điện tử"
            );
        }

        ObjectNode body = request.withObjectProperty("Body");

        ObjectNode danhTinhDienTuNode = body.putObject("DanhTinhDienTu");

        danhTinhDienTuNode.put("TenGoi", danhTinhDienTu.getTenGoi());
        danhTinhDienTuNode.put("TenDinhDanh", danhTinhDienTu.getTenDinhDanh());
        danhTinhDienTuNode.put("MaDinhDanh", danhTinhDienTu.getMaDinhDanh());

        if (JsonUtils.isEmpty(body)) {
            return;
        }


    }

    private void checkPermission() {
        dataPermissionService.checkPermission();
    }
}