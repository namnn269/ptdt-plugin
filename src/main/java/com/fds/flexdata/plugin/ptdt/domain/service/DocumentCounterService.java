package com.fds.flexdata.plugin.ptdt.domain.service;

import com.fds.flexdata.plugin.ptdt.application.http.request.DocumentCounterV2Request;
import com.fds.flexdata.plugin.ptdt.application.http.response.DocumentCounterResponse;

import java.util.List;
import java.util.Map;

public interface DocumentCounterService {

    Map<String, Long> count(String ns, String doiTuongDuLieu);

    Map<String, Long> count(String ns, List<String> doiTuongDuLieuList);

    List<DocumentCounterResponse> count(String ns, DocumentCounterV2Request request);

}
