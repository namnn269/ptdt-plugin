package com.fds.flexdata.plugin.ptdt.service;

import com.fds.flex.user.context.UserContextHolder;
import com.fds.flexdata.plugin.ptdt.domain.dto.ChiTietLoi;
import com.fds.flexdata.plugin.ptdt.shared.DanhTinhDienTuUtils;
import com.fds.flexdata.pluginapi.CommonFunctionHandler;
import com.fds.flexdata.pluginapi.exception.AppException;
import com.fds.flexdata.pluginapi.object_value.DetailError;
import com.fds.flexdata.pluginapi.object_value.MessageCode;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserAccessService {

    private static final String MA_VAI_TRO_CAN_BO_CUC = "03";

    private final CommonFunctionHandler commonFunctionHandler;

    public UserAccess getCurrentUserAccess() {
        String maSoID = UserContextHolder.getContext()
                .getUser()
                .getDanhTinhDienTu()
                .getMaSoID();

        if (ObjectUtils.isEmpty(maSoID)) {
            throwError("User", "Không xác định được người dùng đăng nhập!");
        }

        Document userDoc = DanhTinhDienTuUtils.getDanhTinhDienTuByMaSoID(
                commonFunctionHandler,
                maSoID
        );

        if (userDoc == null) {
            throwError("User", "Không tìm thấy thông tin tài khoản!");
        }

        boolean admin = Boolean.TRUE.equals(
                userDoc.getBoolean("TaiKhoanQuanTri", false)
        );

        boolean canBoCuc = userDoc.getList(
                        "VaiTroSuDung",
                        Document.class,
                        Collections.emptyList()
                )
                .stream()
                .map(item -> item.getString("MaMuc"))
                .anyMatch(MA_VAI_TRO_CAN_BO_CUC::equals);

        Set<String> allowed = userDoc.getList(
                        "PhanVungDuLieuTruyCap",
                        Document.class,
                        Collections.emptyList()
                )
                .stream()
                .map(item -> item.get("MaMuc"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(maMuc -> !maMuc.isEmpty())
                .collect(Collectors.toSet());


        return new UserAccess(admin, canBoCuc, allowed);
    }

    private void throwError(String field, String message) {
        throw new AppException(MessageCode.LOI_DU_LIEU, MessageCode.LOI_DU_LIEU.getValue(), DetailError.E4, List.of(new ChiTietLoi(field, message)));
    }

    public record UserAccess(boolean admin, boolean canBoCuc, Set<String> allowed) {
    }
}