package com.fds.flexdata.plugin.ptdt.domain.handler_advice.chuong_trinh_phat_trien_do_thi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fds.flexdata.plugin.ptdt.domain.dto.ChiTietLoi;
import com.fds.flexdata.plugin.ptdt.service.DataPermissionService;
import com.fds.flexdata.plugin.ptdt.service.EntityRelationService;
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
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Component
@Extension
public class CreateChuongTrinhPhatTrienDoThiHandlerAdvice implements HandlerAdvice, ExtensionPoint {

    private final HandlerAdviceKey key;
    private final DataPermissionService dataPermissionService;
    private final EntityRelationService entityRelationService;

    public CreateChuongTrinhPhatTrienDoThiHandlerAdvice(Environment env, DataPermissionService dataPermissionService, EntityRelationService entityRelationService) {
        this.dataPermissionService = dataPermissionService;
        this.entityRelationService = entityRelationService;
        String csdl = env.getProperty("app.datasource.namespace.ptdt", "csdl-ptdt");
        this.key = new HandlerAdviceKey(csdl, "T_ChuongTrinhPhatTrienDoThi", OpenAPI.Type.CREATE);
    }

    @Override
    public HandlerAdviceKey getKey() {
        return key;
    }

    @Override
    public void beforeProcess(MongoDatabase database, ClientSession session, ObjectNode request) {
        validateBeforeCreating(database, session, request);
        checkPermission(request);
    }

    private void checkPermission(ObjectNode request) {
        JsonNode body = request.path("Body");
        if (JsonUtils.isEmpty(body)) { return; }
        String tinhThanh = body.path("DonViThucHien").path("MaMuc").asText();

        dataPermissionService.checkTinhThanh(tinhThanh);
    }

    private void validateBeforeCreating(MongoDatabase database, ClientSession session, ObjectNode request) {

        JsonNode banTin = request.path("Body");
        if (JsonUtils.isEmpty(banTin)) {
            return;
        }
        String tinhThanhMaMuc = banTin.path("DonViThucHien").path("MaMuc").asText();

        Map<String, String> mddToTenGoiTpMap = banTin.path("DoThiPhatTrien")
                .valueStream()
                .filter(Objects::nonNull)
                .collect(toMap(
                        item -> item.path("MaDinhDanh").asText(),
                        item -> item.path("TenDoThi").asText(),
                        (first, second) -> second
                ));

        if (ObjectUtils.isEmpty(tinhThanhMaMuc) || ObjectUtils.isEmpty(mddToTenGoiTpMap.keySet())) {
            return;
        }

        MongoCollection<Document> coll = database.getCollection("T_DoThi");
        Bson filters = Filters.and(
                Filters.in("MaDinhDanh", mddToTenGoiTpMap.keySet()),
                Filters.eq("TrucThuocTinhThanh.MaMuc", tinhThanhMaMuc)
        );
        Set<String> validTpMDDSet = (session != null ? coll.find(session, filters) : coll.find(filters))
                .projection(Projections.include("MaDinhDanh"))
                .into(new HashSet<>())
                .stream()
                .map(doc -> doc.getString("MaDinhDanh"))
                .collect(toSet());

        Map<String, String> unexpectedData = mddToTenGoiTpMap.entrySet()
                .stream()
                .filter(entry -> !validTpMDDSet.contains(entry.getKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> second));

        if (!unexpectedData.isEmpty()) {
            String tenTinhThanh = banTin.path("DonViThucHien").path("TenMuc").asText();
            List<ChiTietLoi> chiTietLoiList = unexpectedData.entrySet()
                    .stream()
                    .map(err -> new ChiTietLoi("DoThiPhatTrien.MaDinhDanh", String.format("%s (%s) không trực thuộc %s (%s)!", err.getValue(), err.getKey(), tenTinhThanh, tinhThanhMaMuc)))
                    .toList();

            throw new AppException(
                    MessageCode.LOI_DU_LIEU,
                    MessageCode.LOI_DU_LIEU.getValue(),
                    DetailError.E4,
                    chiTietLoiList
            );
        }
    }
}