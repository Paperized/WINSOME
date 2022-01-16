package it.winsome.common.json;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import it.winsome.common.entity.abstracts.BaseSocialEntity;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class JsonDeserializeIdOnly<T extends BaseSocialEntity> implements JsonDeserializer<T> {
    private final Class<T> clazz;

    public JsonDeserializeIdOnly(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public T deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        try {
            T instance = clazz.newInstance();
            instance.setId(jsonElement.getAsInt());
            return instance;
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
}
