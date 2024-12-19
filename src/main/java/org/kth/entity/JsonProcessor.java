package org.kth.entity;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonProcessor {
    private final Gson gson;

    public JsonProcessor() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public String processJson(String jsonString) {
        // JsonReader를 사용하여 JSON 문자열 읽기
        JsonReader reader = new JsonReader(new StringReader(jsonString));
        reader.setLenient(true); // 엄격 모드 비활성화

        // JSON 문자열을 Map으로 변환
        Map<String, Object> tempMap = gson.fromJson(reader, Map.class);

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
                        String[] values = record.getAsJsonObject().get("value").getAsString().split("\\|", -1);

                        // header의 키와 값의 타입에 따라 변환
                        int index = 0;
                        for (String headerKey : headers.keySet()) {
                            String type = headers.get(headerKey); // 필드 타입 가져오기
                            String value = values[index]; // 분리된 값

                            // 타입에 따라 변환
                            switch (type) {
                                case "D":
                                    recordMap.put(headerKey, Double.parseDouble(value)); // Double로 변환
                                    break;
                                case "I":
                                    recordMap.put(headerKey, Integer.parseInt(value)); // Integer로 변환
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
}