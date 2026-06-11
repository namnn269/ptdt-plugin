package com.fds.flexdata.plugin.ptdt.application.http.controller;

import com.fds.flexdata.plugin.ptdt.application.http.request.DocumentCounterV2Request;
import com.fds.flexdata.plugin.ptdt.domain.service.DocumentCounterService;
import com.fds.flexdata.pluginapi.object_value.MessageCode;
import com.fds.flexdata.pluginapi.response.CommonResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Extension
public class DocumentCounterController implements ExtensionPoint {

    private final DocumentCounterService service;

    @GetMapping("/{Namespace:csdl-kdlm|csdlpt|csdl-doanh-nghiep|csdl-ndkpt|csdl-kcht|csdl-dung-chung}/document-counter/v1/count")
    public CommonResponse getDanhMuc(@PathVariable("Namespace") String ns,
                                     @RequestParam String doiTuongDuLieu) {
        return CommonResponse.builder()
                .maLoi(MessageCode.THANH_CONG)
                .moTa(MessageCode.THANH_CONG.getValue())
                .duLieu(service.count(ns, doiTuongDuLieu))
                .build();
    }

    @PostMapping("/{Namespace:csdl-kdlm|csdlpt|csdl-doanh-nghiep|csdl-ndkpt|csdl-kcht|csdl-dung-chung}/document-counter/v2/count")
    public CommonResponse countDocumentV2(@PathVariable("Namespace") String ns,
                                          @RequestBody @Valid @NotNull DocumentCounterV2Request request) {
        return CommonResponse.builder()
                .maLoi(MessageCode.THANH_CONG)
                .moTa(MessageCode.THANH_CONG.getValue())
                .duLieu(service.count(ns, request))
                .build();
    }
}
