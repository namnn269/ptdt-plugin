package com.fds.flexdata.plugin.ptdt.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fds.flex.user.context.UserContextHolder;
import com.fds.flexdata.plugin.ptdt.domain.dto.ChiTietLoi;
import com.fds.flexdata.pluginapi.CommonFunctionHandler;
import com.fds.flexdata.pluginapi.exception.AppException;
import com.fds.flexdata.pluginapi.object_value.DetailError;
import com.fds.flexdata.pluginapi.object_value.MessageCode;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public final class DataPermissionValidationUtils {

    private DataPermissionValidationUtils() {
    }

    public static void validateTinhThanhAccess(String trucThuocTinhThanhMaMuc, CommonFunctionHandler commonFunctionHandler) {
        validateMaMucAccess(
                trucThuocTinhThanhMaMuc,
                commonFunctionHandler,
                "BanTinDuLieu.TrucThuocTinhThanh.MaMuc",
                "Người dùng không có quyền thao tác với tỉnh/thành: "
        );
    }

    public static void validateXaPhuongAccess(String xaPhuongMaMuc, CommonFunctionHandler commonFunctionHandler) {
        validateMaMucAccess(
                xaPhuongMaMuc,
                commonFunctionHandler,
                "BanTinDuLieu.DiaBanTrucThuoc.XaPhuong.MaMuc",
                "Người dùng không có quyền thao tác với xã/phường: "
        );
    }

    public static void validateMaMucAccess(String maMuc, CommonFunctionHandler commonFunctionHandler, String errorField, String errorMessagePrefix) {
        if (ObjectUtils.isEmpty(maMuc)) {
            return;
        }

        String maSoID = UserContextHolder.getContext()
                .getUser()
                .getDanhTinhDienTu()
                .getMaSoID();

        if (ObjectUtils.isEmpty(maSoID)) {
            throwError("User", "Không xác định được người dùng đăng nhập!");
        }

        Document userDoc = DanhTinhDienTuUtils.getDanhTinhDienTuByMaSoID(commonFunctionHandler, maSoID);

        if (userDoc == null) {
            throwError("User", "Không tìm thấy thông tin tài khoản!");
        }

        Boolean taiKhoanQuanTri = userDoc.getBoolean(
                "TaiKhoanQuanTri",
                false
        );

        if (Boolean.TRUE.equals(taiKhoanQuanTri)) {
            return;
        }

        Set<String> maMucDuocTruyCap = userDoc.getList(
                        "PhanVungDuLieuTruyCap",
                        Document.class,
                        Collections.emptyList()
                )
                .stream()
                .map(item -> item.getString("MaMuc"))
                .filter(ma -> !ObjectUtils.isEmpty(ma))
                .collect(toSet());

        if (!maMucDuocTruyCap.contains(maMuc)) {
            throwError(
                    errorField,
                    errorMessagePrefix + maMuc
            );
        }
    }

    public static void validateEntityBelongsToTinhThanh(MongoDatabase database, ClientSession session, String collectionName, String entityMaDinhDanhField, String entityTinhThanhMaMucField, String tinhThanhMaMuc, String tinhThanhTenMuc, String errorField, Map<String, String> maDinhDanhToTenMap) {
        if (ObjectUtils.isEmpty(collectionName)
                || ObjectUtils.isEmpty(entityMaDinhDanhField)
                || ObjectUtils.isEmpty(entityTinhThanhMaMucField)
                || ObjectUtils.isEmpty(tinhThanhMaMuc)
                || ObjectUtils.isEmpty(maDinhDanhToTenMap)) {
            return;
        }

        MongoCollection<Document> coll = database.getCollection(collectionName);

        Bson filters = Filters.and(
                Filters.in(entityMaDinhDanhField, maDinhDanhToTenMap.keySet()),
                Filters.eq(entityTinhThanhMaMucField, tinhThanhMaMuc)
        );

        Set<String> validMaDinhDanhSet = (session != null
                ? coll.find(session, filters)
                : coll.find(filters))
                .projection(Projections.include(entityMaDinhDanhField))
                .into(new ArrayList<>())
                .stream()
                .map(doc -> getStringByPath(doc, entityMaDinhDanhField))
                .filter(Objects::nonNull)
                .collect(toSet());

        Map<String, String> invalidMap = maDinhDanhToTenMap.entrySet()
                .stream()
                .filter(entry -> !validMaDinhDanhSet.contains(entry.getKey()))
                .collect(toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> second
                ));

        if (invalidMap.isEmpty()) {
            return;
        }

        List<ChiTietLoi> errors = invalidMap.entrySet()
                .stream()
                .map(err -> new ChiTietLoi(
                        errorField,
                        String.format(
                                "%s (%s) không trực thuộc %s (%s)!",
                                err.getValue(),
                                err.getKey(),
                                tinhThanhTenMuc,
                                tinhThanhMaMuc
                        )
                ))
                .toList();

        throw new AppException(
                MessageCode.LOI_DU_LIEU,
                MessageCode.LOI_DU_LIEU.getValue(),
                DetailError.E4,
                errors
        );
    }

    public static Map<String, String> extractMaDinhDanhToTenMap(JsonNode parentNode, String arrayFieldName, String maDinhDanhFieldName, String tenFieldName) {
        return parentNode.path(arrayFieldName)
                .valueStream()
                .filter(Objects::nonNull)
                .filter(item -> !ObjectUtils.isEmpty(
                        item.path(maDinhDanhFieldName).asText()
                ))
                .collect(toMap(
                        item -> item.path(maDinhDanhFieldName).asText(),
                        item -> item.path(tenFieldName).asText(),
                        (first, second) -> second
                ));
    }

    private static String getStringByPath(Document document, String fieldPath) {
        if (document == null || ObjectUtils.isEmpty(fieldPath)) {
            return null;
        }

        String[] fields = fieldPath.split("\\.");
        Object current = document;

        for (String field : fields) {
            if (!(current instanceof Document currentDocument)) {
                return null;
            }

            current = currentDocument.get(field);

            if (current == null) {
                return null;
            }
        }

        return current.toString();
    }

    private static void throwError(String field, String message) {
        throw new AppException(
                MessageCode.LOI_DU_LIEU,
                MessageCode.LOI_DU_LIEU.getValue(),
                DetailError.E4,
                List.of(new ChiTietLoi(field, message))
        );
    }
}