package it.winsome.common.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import it.winsome.common.entity.Post;

import java.io.IOException;

public class PostIdJsonAdapter extends TypeAdapter<Post> {
    @Override
    public void write(JsonWriter out, Post value) throws IOException {
        out.value(value.getId());
    }

    @Override
    public Post read(JsonReader in) throws IOException {
        return new Post(in.nextInt());
    }
}