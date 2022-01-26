package it.winsome.common.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import it.winsome.common.entity.Comment;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class PostCommentMapJsonAdapter extends TypeAdapter<Map<Comment, Comment>> {
    @Override
    public void write(JsonWriter out, Map<Comment, Comment> value) throws IOException {
        out.beginArray();
        for(Comment comment : value.values()) {
            comment.prepareRead();
            out.value(comment.getId());
            comment.releaseRead();
        }
        out.endArray();
    }

    @Override
    public Map<Comment, Comment> read(JsonReader in) throws IOException {
        in.beginArray();
        while(in.hasNext()) {
            in.nextInt();
        }
        in.endArray();
        // empty map
        return new LinkedHashMap<>();
    }
}
