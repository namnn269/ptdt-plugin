package com.fds.flexdata.plugin.ptdt.domain.handler_advice.bao_cao_chi_tiet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fds.flexdata.plugin.ptdt.domain.dto.ChiTietLoi;
import com.fds.flexdata.plugin.ptdt.service.UserAccessService;
import com.fds.flexdata.plugin.ptdt.shared.JsonUtils;
import com.fds.flexdata.pluginapi.HandlerAdvice;
import com.fds.flexdata.pluginapi.annotation.OpenAPI;
import com.fds.flexdata.pluginapi.exception.AppException;
import com.fds.flexdata.pluginapi.object_value.DetailError;
import com.fds.flexdata.pluginapi.object_value.MessageCode;
import com.fds.flexdata.pluginapi.query.HandlerAdviceKey;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Slf4j
@Component
@Extension
public class UpdateBaoCaoChiTietHandlerAdvice implements HandlerAdvice, ExtensionPoint {

    private final HandlerAdviceKey key;
    private final UserAccessService userAccessService;

    public UpdateBaoCaoChiTietHandlerAdvice(
            Environment env,
            UserAccessService userAccessService
    ) {
        this.userAccessService = userAccessService;

        String csdl = env.getProperty("app.datasource.namespace.ptdt", "csdl-ptdt");
        this.key = new HandlerAdviceKey(csdl, "T_BaoCaoTongHop", OpenAPI.Type.UPDATE);
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

        checkXaPhuongByUserPhanVung(database, session, body);
    }

    private void checkXaPhuongByUserPhanVung(MongoDatabase database, ClientSession session, JsonNode body) {
        String tinhMa = body.path("TinhThanh").path("MaMuc").asText();
        String tinhTen = body.path("TinhThanh").path("TenMuc").asText();

        String xaMa = body.path("XaPhuong").path("MaMuc").asText();
        String xaTen = body.path("XaPhuong").path("TenMuc").asText();

        UserAccessService.UserAccess access = userAccessService.getCurrentUserAccess();

        if (access.admin() || access.canBoCuc()) {
            return;
        }

        if (ObjectUtils.isEmpty(access.allowed())) {
            throwError("XaPhuong.MaMuc", "Người dùng chưa được phân vùng dữ liệu truy cập!");
        }

        if (ObjectUtils.isEmpty(tinhMa)) {
            throwError("TinhThanh.MaMuc", "Thiếu thông tin tỉnh/thành!");
        }

        if (!access.allowed().contains(xaMa) && !xaMa.isEmpty()) {
            throwError("XaPhuong.MaMuc", String.format("Người dùng không có quyền thao tác với xã/phường %s (%s)!", ObjectUtils.isEmpty(xaTen) ? "xã/phường" : xaTen, xaMa));
        }

        MongoCollection<Document> coll = database.getCollection("C_XaPhuong");

        Bson filter ;
        if (!ObjectUtils.isEmpty(xaMa)) {
            filter = Filters.and(
                    Filters.eq("MaMuc", xaMa),
                    Filters.eq("TinhThanh.MaMuc", tinhMa)
            );
        } else {
            filter = Filters.and(
                    Filters.eq("TinhThanh.MaMuc", tinhMa),
                    Filters.in("MaMuc", access.allowed())
            );
        }
        Document found = coll.find(filter).limit(1).first();
        if (found == null) {
            if (!ObjectUtils.isEmpty(xaMa)) {
                throwError(
                        "XaPhuong.MaMuc",
                        String.format(
                                "Xã/phường %s (%s) không thuộc tỉnh/thành %s (%s)!",
                                ObjectUtils.isEmpty(xaTen) ? "xã/phường" : xaTen,
                                xaMa,
                                ObjectUtils.isEmpty(tinhTen) ? "tỉnh/thành" : tinhTen,
                                tinhMa
                        )
                );
            }

            throwError(
                    "TinhThanh.MaMuc",
                    String.format(
                            "Người dùng không có quyền truy cập dữ liệu thuộc tỉnh/thành %s (%s)!",
                            ObjectUtils.isEmpty(tinhTen) ? "tỉnh/thành" : tinhTen,
                            tinhMa
                    )
            );
        }
    }

    private void throwError(String field, String message) {
        throw new AppException(
                MessageCode.LOI_PHAN_QUYEN,
                MessageCode.LOI_PHAN_QUYEN.getValue(),
                DetailError.E4,
                List.of(new ChiTietLoi(field, message))
        );
    }
}