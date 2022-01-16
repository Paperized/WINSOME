package it.winsome.common.json;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonDeserializeEmptyMap<K, V> implements JsonDeserializer<Map<K, V>> {
    @Override
    public Map<K, V> deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return new LinkedHashMap<>();
    }
}
