package it.winsome.common.json;

import com.google.gson.*;
import it.winsome.common.entity.abstracts.BaseSocialEntity;

import java.lang.reflect.Type;
import java.util.Map;

public class JsonSerializeMapIdOnly<T extends BaseSocialEntity> implements JsonSerializer<Map<T, T>> {

    @Override
    public JsonElement serialize(Map<T, T> ttMap, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonArray res = new JsonArray();
        for(T entity : ttMap.keySet()) {
            res.add(entity.getId());
        }
        return res;
    }
}