package com.fds.flexdata.plugin.ptdt.http.controller;

import com.fds.flexdata.plugin.ptdt.service.BaoCaoService;
import com.fds.flexdata.plugin.ptdt.shared.Constants;
import com.fds.flexdata.pluginapi.query.DataSourceRequest;
import lombok.RequiredArgsConstructor;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(Constants.API_PREFIX + "/v1/bao-cao-chi-tiet")
@RequiredArgsConstructor
@Extension
public class BaoCaoChiTietController implements ExtensionPoint {
    @Autowired
    BaoCaoService baoCaoService;

    @GetMapping("/total")
    public ResponseEntity<?> total(@RequestParam String maTinhThanh) {
        return ResponseEntity.ok(baoCaoService.total(maTinhThanh));
    }
}
