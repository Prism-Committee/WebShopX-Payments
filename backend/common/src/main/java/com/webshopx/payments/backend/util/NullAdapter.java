package com.webshopx.payments.backend.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class NullAdapter extends TypeAdapter<Object> {
    @Override
    public void write(JsonWriter out, Object value) throws IOException {
    }
    @Override
    public Object read(JsonReader in) throws IOException {
        return null;
    }
}
