package com.fds.flexdata.plugin.ptdt.service;

import com.fds.flexdata.plugin.ptdt.domain.dto.ChiTietLoi;
import com.fds.flexdata.plugin.ptdt.service.UserAccessService;
import com.fds.flexdata.pluginapi.exception.AppException;
import com.fds.flexdata.pluginapi.object_value.DetailError;
import com.fds.flexdata.pluginapi.object_value.MessageCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataPermissionService {

    private final UserAccessService userAccessService;

    public void checkPermission() {
        check("", "BanTinDuLieu.VaiTroSuDung.MaMuc", "Người dùng không có quyền thao tác ");
    }

    public void checkTinhThanh(String maMuc) {
        check(maMuc, "BanTinDuLieu.TrucThuocTinhThanh.MaMuc", "Người dùng không có quyền thao tác với tỉnh/thành: ");
    }

    public void checkXaPhuong(String maMuc) {
        check(maMuc, "BanTinDuLieu.DiaBanTrucThuoc.XaPhuong.MaMuc", "Người dùng không có quyền thao tác với xã/phường: ");
    }

    public void check(String maMuc, String field, String messagePrefix) {

        UserAccessService.UserAccess user = userAccessService.getCurrentUserAccess();

        if (user.admin() || user.canBoCuc()) {
            return;
        }

        if (!user.allowed().contains(maMuc)) {

            throw new AppException(
                    MessageCode.LOI_DU_LIEU,
                    MessageCode.LOI_DU_LIEU.getValue(),
                    DetailError.E4,
                    List.of(new ChiTietLoi(field, messagePrefix + maMuc))
            );
        }

    }
}