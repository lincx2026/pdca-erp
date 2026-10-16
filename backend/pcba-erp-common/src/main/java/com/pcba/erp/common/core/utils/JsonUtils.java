package com.pcba.erp.common.core.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Jackson 工具 */
public final class JsonUtils {
    private JsonUtils() {}
    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper();
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        MAPPER.setDateFormat(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        MAPPER.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
    }

    public static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化失败", e);
        }
    }

    public static <T> T fromJson(String s, Class<T> t) {
        try {
            return MAPPER.readValue(s, t);
        } catch (Exception e) {
            throw new RuntimeException("反序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object o) {
        if (o == null) return Collections.emptyMap();
        return MAPPER.convertValue(o, Map.class);
    }

    public static <T> List<T> fromList(String s, Class<T> t) {
        try {
            return MAPPER.readValue(s, MAPPER.getTypeFactory().constructCollectionType(List.class, t));
        } catch (Exception e) {
            throw new RuntimeException("反序列化失败", e);
        }
    }
}
