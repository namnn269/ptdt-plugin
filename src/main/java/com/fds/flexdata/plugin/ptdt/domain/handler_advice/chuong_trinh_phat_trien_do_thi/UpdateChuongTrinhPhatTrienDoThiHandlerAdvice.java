package com.fds.flexdata.plugin.ptdt.domain.handler_advice.chuong_trinh_phat_trien_do_thi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fds.flex.user.context.UserContextHolder;
import com.fds.flexdata.plugin.ptdt.domain.dto.ChiTietLoi;
import com.fds.flexdata.plugin.ptdt.shared.DanhTinhDienTuUtils;
import com.fds.flexdata.plugin.ptdt.shared.DataPermissionValidationUtils;
import com.fds.flexdata.plugin.ptdt.shared.JsonUtils;
import com.fds.flexdata.pluginapi.CommonFunctionHandler;
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

import java.util.*;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Component
@Extension
public class UpdateChuongTrinhPhatTrienDoThiHandlerAdvice implements HandlerAdvice, ExtensionPoint {

    private final HandlerAdviceKey key;
    private final CommonFunctionHandler commonFunctionHandler;
    public UpdateChuongTrinhPhatTrienDoThiHandlerAdvice(Environment env, CommonFunctionHandler commonFunctionHandler) {
        this.commonFunctionHandler = commonFunctionHandler;
        String csdl = env.getProperty("app.datasource.namespace.ptdt", "csdl-ptdt");
        key = new HandlerAdviceKey(csdl, "T_ChuongTrinhPhatTrienDoThi", OpenAPI.Type.UPDATE);
    }

    @Override
    public HandlerAdviceKey getKey() {
        return key;
    }

    @Override
    public void beforeProcess(MongoDatabase database, ClientSession session, ObjectNode request) {
        validateBeforeUpdating(database, session, request);
        validatePermissionAndDoThiRelation(database, session, request);
    }

    private void validateBeforeUpdating(MongoDatabase database, ClientSession session, ObjectNode request) {
        JsonNode banTin = request.path("Body");
        if (JsonUtils.isEmpty(banTin)) {
            return;
        }

        String tinhThanhMaMuc = banTin.path("DonViThucHien").path("MaMuc").asText();
        String tinhThanhTenMuc = banTin.path("DonViThucHien").path("TenMuc").asText();
        String mdd = request.path("MaDinhDanh").asText();

        Map<String, String> mddToTenGoiTpMap = banTin.path("DoThiPhatTrien")
                .valueStream()
                .filter(Objects::nonNull)
                .collect(toMap(
                        item -> item.path("MaDinhDanh").asText(),
                        item -> item.path("TenDoThi").asText(),
                        (first, second) -> second
                ));

        if (ObjectUtils.isEmpty(mddToTenGoiTpMap.keySet()) || ObjectUtils.isEmpty(mdd)) {
            return;
        }

        if (ObjectUtils.isEmpty(tinhThanhMaMuc)) {
            MongoCollection<Document> coll = database.getCollection("T_ChuongTrinhPhatTrienDoThi");
            Bson filters = Filters.eq("MaDinhDanh", mdd);
            Document tinhThanh = (session != null ? coll.find(session, filters) : coll.find(filters))
                    .limit(1)
                    .projection(Projections.include("DonViThucHien.MaMuc", "DonViThucHien.TenMuc"))
                    .first();
            if (tinhThanh == null) {
                throw new AppException(
                        MessageCode.LOI_DU_LIEU,
                        "Không tìm thấy chương trình phát triển đô thị.",
                        DetailError.E4,
                        List.of(new ChiTietLoi("MaDinhDanh", mdd))
                );
            }
            tinhThanhMaMuc = tinhThanh.get("DonViThucHien", Document.class).getString("MaMuc");
            if (ObjectUtils.isEmpty(tinhThanhTenMuc)) {
                tinhThanhTenMuc = tinhThanh.get("DonViThucHien", Document.class).getString("TenMuc");
            }
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
            String finalTinhThanhMaMuc = tinhThanhMaMuc;
            String finalTinhThanhTenMuc = tinhThanhTenMuc;
            List<ChiTietLoi> chiTietLoiList = unexpectedData.entrySet()
                    .stream()
                    .map(err -> new ChiTietLoi("DoThiPhatTrien.MaDinhDanh", String.format("%s (%s) không trực thuộc %s (%s)!", err.getValue(), err.getKey(), finalTinhThanhTenMuc, finalTinhThanhMaMuc)))
                    .toList();

            throw new AppException(
                    MessageCode.LOI_DU_LIEU,
                    MessageCode.LOI_DU_LIEU.getValue(),
                    DetailError.E4,
                    chiTietLoiList
            );
        }
    }

    private void validatePermissionAndDoThiRelation(
            MongoDatabase database,
            ClientSession session,
            ObjectNode request
    ) {
        JsonNode banTin = request.path("Body");

        if (JsonUtils.isEmpty(banTin)) {
            return;
        }

        String tinhThanhMaMuc = banTin.path("DonViThucHien")
                .path("MaMuc")
                .asText();

        String tinhThanhTenMuc = banTin.path("DonViThucHien")
                .path("TenMuc")
                .asText();

        if (ObjectUtils.isEmpty(tinhThanhMaMuc)) {
            return;
        }

        DataPermissionValidationUtils.validateTinhThanhAccess(
                tinhThanhMaMuc,
                commonFunctionHandler
        );

        Map<String, String> doThiMap =
                DataPermissionValidationUtils.extractMaDinhDanhToTenMap(
                        banTin,
                        "DoThiPhatTrien",
                        "MaDinhDanh",
                        "TenDoThi"
                );

        DataPermissionValidationUtils.validateEntityBelongsToTinhThanh(
                database,
                session,
                "T_DoThi",
                "MaDinhDanh",
                "TrucThuocTinhThanh.MaMuc",
                tinhThanhMaMuc,
                tinhThanhTenMuc,
                "DoThiPhatTrien.MaDinhDanh",
                doThiMap
        );
    }
}
