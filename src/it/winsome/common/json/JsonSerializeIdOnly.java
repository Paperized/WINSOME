package it.winsome.common.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import it.winsome.common.entity.abstracts.BaseSocialEntity;

import java.lang.reflect.Type;

public class JsonSerializeIdOnly<T extends BaseSocialEntity> implements JsonSerializer<T> {

    @Override
    public JsonElement serialize(T t, Type type, JsonSerializationContext jsonSerializationContext) {
        return new JsonPrimitive(t.getId());
    }
}
