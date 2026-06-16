package com.fds.flexdata.plugin.ptdt.shared;

import com.fds.flexdata.pluginapi.query.DataSourceRequest;

import java.util.Set;
import java.util.UUID;

public class Constants {
    public static final String X_TAI_NGUYEN_DICH_VU_KEY = "X-TAI-NGUYEN-DICH-VU-KEY";
    public static final String X_IGNORE_AUTHORIZATION_KEY = "X-IGNORE-AUTHORIZATION-KEY";
    public static final String ADMIN_API_PREFIX = "/api/admin";
    public static final String IN_HOUSE_API_PREFIX = "/api/in-house";
    public static final String IN_PULSE_API_PREFIX = "/api/pulse";
    public static final String COMMON_API_PREFIX = "/api/flex-digital";
    public static final String API_PREFIX = "/plugin/api";
    public static final String OFF = "OFF";
    public static final String BASIC = "BASIC";
    public static final String STANDARD = "STANDARD";
    public static final String FULL = "FULL";

    public static final String DIR_PATH = "conf";
    public static final String FILE_NAME = "level-log.txt";

    public static final String STREAM_PREFIX = "stream:";
    public static final String STREAM_CHANNEL_PREFIX = "stream:channel:";
    public static final String STREAM_META_PREFIX = "stream:meta:";

    public static final int MAX_LENGTH_LOG_DATA = 2000;

    public static final int ORDER_5 = 5;
    public static final int ORDER_10 = 10;
    public static final int ORDER_25 = 25;
    public static final int ORDER_50 = 50;
    public static final int ORDER_100 = 100;
    public static final int ORDER_200 = 200;
    public static final int ORDER_300 = 300;
    public static final int ORDER_400 = 400;
    public static final int ORDER_500 = 500;

    private Constants() {
    }


    public static final String INSTANCE_ID = UUID.randomUUID().toString();
    public static final String COMMA = ",";
    public static final String __NS__ = "__ns__";
    public static final String __COLLECTION__ = "__collection__";

    public static final Set<String> WHITE_LIST_ENDPOINTS = Set.of(
            "/error",
            "/api/keycloak/backchannel/logout",
            "/actuator/health",
            IN_HOUSE_API_PREFIX + "/v1/logcollector",
            IN_HOUSE_API_PREFIX + "/v1/test",
            IN_PULSE_API_PREFIX + "/v1/ngan-du-lieu",
            IN_PULSE_API_PREFIX + "/v1/doi-tuong-du-lieu",
            IN_PULSE_API_PREFIX + "/v1/action",
            IN_PULSE_API_PREFIX + "/v1/log/search",
            IN_PULSE_API_PREFIX + "/v1/log/detail"
    );

    private static String namespaceOfThamSoCauHinh;
    public static final Set<DataSourceRequest> IGNORE_HANDLE_DATA = Set.of(
            new DataSourceRequest(namespaceOfThamSoCauHinh, "ThamSoCauHinh")
    );

    static {
        namespaceOfThamSoCauHinh = System.getProperty("app.namespace.entity.m-thamsocauhinh", "csdl-sso");
    }
}
