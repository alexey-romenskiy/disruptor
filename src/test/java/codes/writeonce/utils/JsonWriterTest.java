package codes.writeonce.utils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsonWriterTest {

    private static final BiConsumer<JsonWriter, String> JSON_OBJECT = (w, s) -> w.start(new JsonObject(s));
    private static final BiConsumer<JsonWriter, String> JSON_ARRAY = (w, s) -> w.start(new JsonArray(s));

    @Test
    public void write() {
        check(JSON_OBJECT, "{}");
    }

    @Test
    public void write2() {
        check(JSON_ARRAY, "[]");
    }

    @Test
    public void write3() {
        check(JSON_ARRAY, "[null,true,false,\"\",\"\\rqw\",\"q\\rw\",\"qw\\r\",\"\\\"qw\",\"q\\\"w\",\"qw\\\"\"]");
    }

    @Test
    public void write4() {
        check(JSON_ARRAY, "[\"\\\\qw\",\"q\\\\w\",\"qw\\\\\",\"\\u0000qw\",\"q\\u0000w\",\"qw\\u0000\"]");
    }

    @Test
    public void write5() {
        check(JSON_ARRAY, "[\"фыва\",\"妈/媽\",\"\uD802\uDC00\"]");
    }

    @Test
    public void write6() {
        check(JSON_ARRAY, "[{\"qwe\":[\"rty\"],\"qwe2\":[\"rty2\"]}]");
    }

    @Test
    public void write7() {
        check(JSON_OBJECT, "{\"qwe\":[\"rty\"],\"qwe2\":[\"rty2\"]}");
    }

    @Test
    public void write8() {
        check(JSON_ARRAY, "[-0.0,0.0,0,-123,123]");
    }

    private void check(@Nonnull BiConsumer<JsonWriter, String> consumer, @Nonnull String expected) {
        try (var writer = new JsonWriter()) {
            consumer.accept(writer, expected);
            final var buffer1 = ByteBuffer.allocate(expected.getBytes(UTF_8).length);
            assertFalse(writer.write(buffer1));
            assertEquals(expected, new String(buffer1.array(), buffer1.arrayOffset(), buffer1.position(), UTF_8));
            for (int capacity = 1; capacity <= 1000; capacity++) {
                consumer.accept(writer, expected);
                final var n = expected.getBytes(UTF_8).length;
                final var builder = new ByteArrayOutputStream();
                final var buffer = ByteBuffer.allocate(capacity);
                for (int i = 0; i < (n - 1) / capacity; i++) {
                    assertTrue(writer.write(buffer));
                    builder.write(buffer.array(), buffer.arrayOffset(), buffer.position());
                    buffer.clear();
                }
                assertFalse(writer.write(buffer));
                builder.write(buffer.array(), buffer.arrayOffset(), buffer.position());
                assertEquals(expected, builder.toString(UTF_8));
                assertEquals(expected, builder.toString(UTF_8));
            }
        }
    }
}
