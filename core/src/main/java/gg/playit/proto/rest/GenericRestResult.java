package gg.playit.proto.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

public class GenericRestResult {
    public String status;
    public JsonElement data;
    public Object specialize(Class<?> successClass, Class<?> failClass) {
        return switch (status) {
            case "success" -> ApiClient.GSON.fromJson(data, successClass);
            case "fail" -> ApiClient.GSON.fromJson(data, failClass);
            case "error" -> ApiClient.GSON.fromJson(data, ErrorResponse.class);
            default -> throw new JsonSyntaxException("Invalid discriminant");
        };
    }
}