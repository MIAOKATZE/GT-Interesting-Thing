package com.miaokatze.gtit.trade;

import java.io.IOException;
import java.io.StringReader;

import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;

/** Checks structure iteratively before Gson recursively reads configurable alternatives. */
public final class NekoTradeJson {

    private NekoTradeJson() {}

    public static void validateDepth(String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(true);
            int depth = 0;
            while (true) {
                switch (reader.peek()) {
                    case BEGIN_OBJECT -> {
                        if (++depth > 32) throw new JsonParseException("Trade JSON nesting exceeds 32 levels");
                        reader.beginObject();
                    }
                    case BEGIN_ARRAY -> {
                        if (++depth > 32) throw new JsonParseException("Trade JSON nesting exceeds 32 levels");
                        reader.beginArray();
                    }
                    case END_OBJECT -> {
                        reader.endObject();
                        depth--;
                    }
                    case END_ARRAY -> {
                        reader.endArray();
                        depth--;
                    }
                    case NAME -> reader.nextName();
                    case STRING, NUMBER -> reader.nextString();
                    case BOOLEAN -> reader.nextBoolean();
                    case NULL -> reader.nextNull();
                    case END_DOCUMENT -> {
                        return;
                    }
                }
            }
        } catch (IOException e) {
            throw new JsonParseException("Invalid trade JSON", e);
        }
    }
}
