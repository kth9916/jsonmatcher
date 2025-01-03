package org.kth.entity;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.apache.commons.lang.StringUtils;

import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonProcessor {
    private final Gson gson;

    //    public JsonProcessor() {
//        this.gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
//    }
    public JsonProcessor() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(new TypeToken<Map<String, Object>>() {}.getType(), new JsonDeserializer<Map<String, Object>>() {
            @Override
            public Map<String, Object> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                Map<String, Object> map = context.deserialize(json, Map.class);
                // 추가적인 타입 처리 로직을 여기에 추가
                return map;
            }
        });
        this.gson = builder.setPrettyPrinting().serializeNulls().create();
    }

    public String processJson(String jsonString) {
        // JsonReader를 사용하여 JSON 문자열 읽기
        JsonReader reader = new JsonReader(new StringReader(jsonString));
        reader.setLenient(true); // 엄격 모드 비활성화

        // JSON 문자열을 Map으로 변환
//        Map<String, Object> tempMap = gson.fromJson(reader, Map.class);
        Map<String, Object> tempMap = gson.fromJson(reader, new TypeToken<Map<String, Object>>() {
        }.getType());

        // 'recordSets'에서 변환 작업 수행
        if (tempMap.containsKey("recordSets")) {
            Map<String, Object> recordSets = (Map<String, Object>) tempMap.get("recordSets");
            for (String key : recordSets.keySet()) {
                Map<String, Object> recordSet = (Map<String, Object>) recordSets.get(key);
                if (recordSet.containsKey("records")) {
                    JsonArray records = gson.toJsonTree(recordSet.get("records")).getAsJsonArray();
                    JsonArray transformedRecords = new JsonArray();

                    // 'headers' 가져오기
                    Map<String, String> headers = (Map<String, String>) recordSet.get("headers");

                    // 각 record 변환
                    for (JsonElement record : records) {
                        Map<String, Object> recordMap = new LinkedHashMap<>();
//                        String[] values = record.getAsJsonObject().get("value").getAsString().split("\\|", -1);
                        String[] values = hel(record, headers);

                        // header의 키와 값의 타입에 따라 변환
                        int index = 0;
                        for (String headerKey : headers.keySet()) {
                            String type = headers.get(headerKey); // 필드 타입 가져오기
                            String value = values[index]; // 분리된 값

                            // 타입에 따라 변환
                            switch (type) {
                                case "D":
                                    if (StringUtils.isEmpty(value)) {
                                        recordMap.put(headerKey, null); // 또는 0.0과 같은 기본값
                                    } else {
                                        recordMap.put(headerKey, Double.parseDouble(value)); // Double로 변환
                                    }
                                    break;
                                case "I":
                                    if (StringUtils.isEmpty(value)) {
                                        recordMap.put(headerKey, null); // 또는 0과 같은 기본값
                                    } else if (value.contains(".")) {
                                        recordMap.put(headerKey, (int) Double.parseDouble(value));
                                    } else {
                                        recordMap.put(headerKey, Integer.parseInt(value)); // Integer로 변환
                                    }
                                    break;
                                case "L":
                                    if (StringUtils.isEmpty(value)) {
                                        recordMap.put(headerKey, null); // 또는 0L과 같은 기본값
                                    } else if (value.contains(".")) {
                                        recordMap.put(headerKey, (long) Double.parseDouble(value));
                                    } else {
                                        recordMap.put(headerKey, Long.parseLong(value));
                                    }
                                    break;
                                case "S":
                                default:
                                    recordMap.put(headerKey, value); // String으로 처리
                                    break;
                            }
                            index++;
                        }
                        transformedRecords.add(gson.toJsonTree(recordMap));
                    }

                    // 변환된 records 업데이트
                    recordSet.put("records", transformedRecords);
                }
            }
        }

        // 최종적으로 LinkedTreeMap을 JSON으로 변환하여 반환
        return gson.toJson(tempMap);
    }

    private String[] hel(JsonElement record, Map<String, String> headers) {
        //
        String[] list;
        if (record.getAsJsonObject().get("value") != null) {
            list = record.getAsJsonObject().get("value").getAsString().split("\\|", -1);
        } else {
            List<String> values = new ArrayList<>();
            headers.keySet().forEach(key -> {
                // get()의 결과가 null일 경우도 처리
                String value = record.getAsJsonObject().get(key).isJsonNull()
                        ? null
                        : record.getAsJsonObject().get(key).getAsString();
                values.add(value); // null도 추가됨
            });
            list = values.toArray(new String[0]);
        }

        return list;
    }
}