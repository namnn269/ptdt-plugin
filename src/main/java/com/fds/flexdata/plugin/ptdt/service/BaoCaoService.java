package com.fds.flexdata.plugin.ptdt.service;

import com.fds.flexdata.pluginapi.CommonFunctionHandler;
import com.fds.flexdata.pluginapi.query.DataSourceRequest;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;

@Service
public class BaoCaoService {

    private final CommonFunctionHandler commonFunctionHandler;

    public BaoCaoService(CommonFunctionHandler commonFunctionHandler) {
        this.commonFunctionHandler = commonFunctionHandler;
    }

    public boolean exitBaoCao(String thangBaoCao, Integer namBaoCao) {
        DataSourceRequest dataSourceRequestCanBo =
                new DataSourceRequest("csdl-ptdt", "BaoCaoTongHop");

        List<Document> baoCao = commonFunctionHandler.aggregate(
                dataSourceRequestCanBo,
                List.of(
                        Aggregates.match(
                                Filters.and(
                                        Filters.eq("KyBaoCao.MaMuc", thangBaoCao),
                                        Filters.eq("NamBaoCao", namBaoCao)
                                )
                        )
                )
        );

        return !baoCao.isEmpty();

    }

    public boolean exitBaoCao(String thangBaoCao, Integer namBaoCao, String tinhMa, String xaMa) {
        DataSourceRequest dataSourceRequestCanBo =
                new DataSourceRequest("csdl-ptdt", "BaoCaoTongHop");

        List<Document> baoCao;
        if (ObjectUtils.isEmpty(xaMa)) {
            baoCao = commonFunctionHandler.aggregate(
                    dataSourceRequestCanBo,
                    List.of(
                            Aggregates.match(
                                    Filters.and(
                                            Filters.eq("KyBaoCao.MaMuc", thangBaoCao),
                                            Filters.eq("NamBaoCao", namBaoCao),
                                            Filters.eq("TinhThanh.MaMuc", tinhMa),
                                            Filters.or(
                                                    Filters.eq("XaPhuong.MaMuc", null),
                                                    Filters.eq("XaPhuong.MaMuc", "")
                                            )
                                    )
                            )
                    )
            );
        } else {
            baoCao = commonFunctionHandler.aggregate(
                    dataSourceRequestCanBo,
                    List.of(
                            Aggregates.match(
                                    Filters.and(
                                            Filters.eq("KyBaoCao.MaMuc", thangBaoCao),
                                            Filters.eq("NamBaoCao", namBaoCao),
                                            Filters.eq("TinhThanh.MaMuc", tinhMa),
                                            Filters.eq("XaPhuong.MaMuc", xaMa)
                                    )
                            )
                    )
            );
        }

        return !baoCao.isEmpty();

    }

    public Map<String, Object> total(String maTinhThanh) {

        DataSourceRequest dataSourceRequest =
                new DataSourceRequest("csdl-ptdt", "DoThi");

        List<Document> result = commonFunctionHandler.aggregate(
                dataSourceRequest,
                List.of(
                        Aggregates.match(
                                Filters.eq("TrucThuocTinhThanh.MaMuc", maTinhThanh)
                        ),
                        Aggregates.group(
                                null,
                                Accumulators.sum("tongDanSoDoThi", "$DanSoDoThi"),
                                Accumulators.sum("tongDienTichDoThi", "$DienTichDoThi")
                        )
                )
        );

        if (result.isEmpty()) {
            return Map.of(
                    "tongDanSoDoThi", 0,
                    "tongDienTichDoThi", 0
            );
        }

        Document doc = result.get(0);

        return Map.of(
                "TongDanSoDoThi", doc.get("tongDanSoDoThi"),
                "TongDienTichDoThi", doc.get("tongDienTichDoThi")
        );
    }

}
