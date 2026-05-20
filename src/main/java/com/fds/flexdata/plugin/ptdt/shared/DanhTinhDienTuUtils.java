package com.fds.flexdata.plugin.ptdt.shared;

import com.fds.flexdata.pluginapi.CommonFunctionHandler;
import com.fds.flexdata.pluginapi.query.DataSourceRequest;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;

import java.util.List;

public final class DanhTinhDienTuUtils {

    private static final String SSO_NAMESPACE = "csdl-sso";
    private static final String COLLECTION_NAME = "T_DanhTinhDienTu";

    private DanhTinhDienTuUtils() {
    }

    public static Document getDanhTinhDienTuByMaSoID(CommonFunctionHandler commonFunctionHandler, String maSoID) {

        if (maSoID == null || maSoID.isBlank()) {
            return null;
        }

        DataSourceRequest dataSourceRequest = new DataSourceRequest(SSO_NAMESPACE, COLLECTION_NAME);

        List<Document> documents = commonFunctionHandler.aggregate(dataSourceRequest,
                List.of(Aggregates.match(Filters.eq("MaSoID", maSoID)),
                        Aggregates.project(Projections.fields(Projections.include("TaiKhoanQuanTri", "PhanVungDuLieuTruyCap")))));
        if (documents == null || documents.isEmpty()) {
            return null;
        }

        return documents.getFirst();
    }
}