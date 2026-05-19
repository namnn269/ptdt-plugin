package com.fds.flexdata.plugin.ptdt.domain.handler_advice.do_thi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fds.flexdata.plugin.ptdt.shared.DataPermissionValidationUtils;
import com.fds.flexdata.plugin.ptdt.shared.JsonUtils;
import com.fds.flexdata.pluginapi.CommonFunctionHandler;
import com.fds.flexdata.pluginapi.HandlerAdvice;
import com.fds.flexdata.pluginapi.annotation.OpenAPI;
import com.fds.flexdata.pluginapi.query.HandlerAdviceKey;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.HashSet;
import java.util.Set;

@Component
@Extension
public class UpdateDoThiHandlerAdvice implements HandlerAdvice, ExtensionPoint {

    private final HandlerAdviceKey key;
    private final CommonFunctionHandler commonFunctionHandler;

    public UpdateDoThiHandlerAdvice(
            Environment env,
            CommonFunctionHandler commonFunctionHandler
    ) {
        this.commonFunctionHandler = commonFunctionHandler;

        String csdl = env.getProperty("app.datasource.namespace.ptdt", "csdl-ptdt");
        this.key = new HandlerAdviceKey(
                csdl,
                "T_DoThi",
                OpenAPI.Type.UPDATE
        );
    }

    @Override
    public HandlerAdviceKey getKey() {
        return key;
    }

    @Override
    public void beforeProcess(MongoDatabase database, ClientSession session, ObjectNode request) {
        JsonNode banTin = request.path("Body");

        if (JsonUtils.isEmpty(banTin)) {
            return;
        }

        validatePhanVungDuLieuTruyCap(banTin);
    }

    private void validatePhanVungDuLieuTruyCap(JsonNode banTin) {
        JsonNode diaBanNode = banTin.path("DiaBanTrucThuoc");

        String trucThuocTinhThanhMaMuc = banTin.path("TrucThuocTinhThanh")
                .path("MaMuc")
                .asText();

        Set<String> tinhThanhMaMucSet = new HashSet<>();
        Set<String> xaPhuongMaMucSet = new HashSet<>();

        if (diaBanNode.isArray() && !diaBanNode.isEmpty()) {
            for (JsonNode diaBan : diaBanNode) {
                String tinhThanhMaMuc = diaBan.path("TinhThanh")
                        .path("MaMuc")
                        .asText();

                String xaPhuongMaMuc = diaBan.path("XaPhuong")
                        .path("MaMuc")
                        .asText();

                if (!ObjectUtils.isEmpty(tinhThanhMaMuc)) {
                    tinhThanhMaMucSet.add(tinhThanhMaMuc);
                }

                if (!ObjectUtils.isEmpty(xaPhuongMaMuc)) {
                    xaPhuongMaMucSet.add(xaPhuongMaMuc);
                }
            }
        }

        if (ObjectUtils.isEmpty(trucThuocTinhThanhMaMuc)
                && tinhThanhMaMucSet.isEmpty()
                && xaPhuongMaMucSet.isEmpty()) {
            return;
        }

        DataPermissionValidationUtils.validateTinhThanhAccess(
                trucThuocTinhThanhMaMuc,
                commonFunctionHandler
        );

        for (String tinhThanhMaMuc : tinhThanhMaMucSet) {
            DataPermissionValidationUtils.validateTinhThanhAccess(
                    tinhThanhMaMuc,
                    commonFunctionHandler
            );
        }

        for (String xaPhuongMaMuc : xaPhuongMaMucSet) {
            DataPermissionValidationUtils.validateXaPhuongAccess(
                    xaPhuongMaMuc,
                    commonFunctionHandler
            );
        }
    }
}