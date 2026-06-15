package com.fds.flexdata.plugin.ptdt.service;

import com.fds.flexdata.pluginapi.CommonFunctionHandler;
import com.fds.flexdata.pluginapi.query.DataSourceRequest;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.List;

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


}
