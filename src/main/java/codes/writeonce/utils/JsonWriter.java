package codes.writeonce.utils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Float.floatToRawIntBits;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class JsonWriter implements AutoCloseable {

    private static final byte[][] SPECIALS = new byte[32][];
    private static final byte[] DOUBLE_QUOTE = "\\\"".getBytes(UTF_8);
    private static final byte[] REVERSE_SOLIDUS = "\\\\".getBytes(UTF_8);
    private static final byte[] NULL_BYTES = "null".getBytes(UTF_8);
    private static final byte[] FALSE_BYTES = "false".getBytes(UTF_8);
    private static final byte[] TRUE_BYTES = "true".getBytes(UTF_8);

    static {
        for (int i = 0; i < SPECIALS.length; i++) {
            SPECIALS[i] = (switch (i) {
                case '\b' -> "\\b";//8
                case '\t' -> "\\t";//9
                case '\n' -> "\\n";//10
                case '\f' -> "\\f";//12
                case '\r' -> "\\r";//13
                default -> String.format("\\u%04X", i);
            }).getBytes(UTF_8);
        }
    }

    private State state = State.END;

    private Iterator<Map.Entry<String, Object>> jsonObjectIterator;

    private Iterator<Object> jsonArrayIterator;

    private final ArrayDeque<State> states = new ArrayDeque<>();

    private final ArrayDeque<Iterator<Map.Entry<String, Object>>> jsonObjectIterators = new ArrayDeque<>();

    private final ArrayDeque<Iterator<Object>> jsonArrayIterators = new ArrayDeque<>();

    private Object entryValue;

    private String stringValue;

    private int stringPosition;

    private byte[] bytesValue;

    private final byte[] remainedBytes = new byte[3];

    private int bytesPosition;

    private final StringBuilder builder = new StringBuilder();

    public void cleanup() {
        state = State.END;
        jsonObjectIterator = null;
        jsonArrayIterator = null;
        states.clear();
        jsonObjectIterators.clear();
        jsonArrayIterators.clear();
        entryValue = null;
        stringValue = null;
        stringPosition = 0;
        bytesValue = null;
        Arrays.fill(remainedBytes, (byte) 0);
        bytesPosition = 0;
        builder.setLength(0);
    }

    @Override
    public void close() {
        cleanup();
    }

    private enum State {
        END,
        OBJECT_START,
        OBJECT_END,
        OBJECT_COMMA,
        OBJECT_COLON,
        OBJECT_COLON_LAST,
        ARRAY_START,
        ARRAY_END,
        ARRAY_COMMA,
        QUOTED_STRING_START,
        QUOTED_STRING_END,
        QUOTED_STRING,
        QUOTED_STRING_BYTES,
        BYTES,
        BUILDER
    }

    @Nonnull
    public JsonWriter start(@Nonnull JsonObject jsonObject) {
        cleanup();
        jsonObjectIterator = jsonObject.isEmpty() ? null : jsonObject.iterator();
        state = State.OBJECT_START;
        states.push(State.END);
        return this;
    }

    @Nonnull
    public JsonWriter start(@Nonnull JsonArray jsonArray) {
        cleanup();
        this.jsonArrayIterator = jsonArray.isEmpty() ? null : jsonArray.iterator();
        state = State.ARRAY_START;
        states.push(State.END);
        return this;
    }

    private void popState() {

        state = states.pop();
        switch (state) {
            case OBJECT_COMMA, OBJECT_COLON -> jsonObjectIterator = jsonObjectIterators.pop();
            case ARRAY_COMMA -> jsonArrayIterator = jsonArrayIterators.pop();
        }
    }

    /**
     * @return <code>true</code> if we need more buffers
     */
    public boolean write(@Nonnull ByteBuffer byteBuffer) {

        var r = byteBuffer.remaining();

        while (r != 0) {
            switch (state) {
                case END -> {
                    return false;
                }
                case OBJECT_START -> r = onObjectStartHR(byteBuffer, r);
                case OBJECT_END -> r = onObjectEndHR(byteBuffer, r);
                case OBJECT_COMMA -> r = onObjectCommaHR(byteBuffer, r);
                case QUOTED_STRING_BYTES -> r = onQuotedStringBytesHRS(byteBuffer, r);
                case BYTES -> r = onBytesHRS(byteBuffer, r);
                case BUILDER -> r = onBuilderHRS(byteBuffer, r);
                case OBJECT_COLON -> r = onObjectColonHR(byteBuffer, r);
                case OBJECT_COLON_LAST -> r = onObjectColonLastHR(byteBuffer, r);
                case ARRAY_START -> r = onArrayStartHR(byteBuffer, r);
                case ARRAY_END -> r = onArrayEndHR(byteBuffer, r);
                case ARRAY_COMMA -> r = onArrayCommaHR(byteBuffer, r);
                case QUOTED_STRING_START -> r = onQuotedStringStartHR(byteBuffer, r);
                case QUOTED_STRING_END -> r = onQuotedStringEndHR(byteBuffer, r);
                case QUOTED_STRING -> r = onQuotedStringHR(byteBuffer, r);
                default -> throw new IllegalStateException();
            }
        }

        return state != State.END;
    }

    private int onObjectStartHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        byteBuffer.put((byte) '{');
        remaining--;
        if (jsonObjectIterator == null) {
            return onObjectEnd(byteBuffer, remaining);
        } else {
            return writeObjectFieldName(byteBuffer, remaining);
        }
    }

    private int onArrayStartHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        byteBuffer.put((byte) '[');
        remaining--;
        if (jsonArrayIterator == null) {
            return onArrayEnd(byteBuffer, remaining);
        } else {
            return writeArrayValue(byteBuffer, remaining);
        }
    }

    private int onObjectEnd(@Nonnull ByteBuffer byteBuffer, int remaining) {
        if (remaining == 0) {
            state = State.OBJECT_END;
        } else {
            remaining = onObjectEndHR(byteBuffer, remaining);
        }
        return remaining;
    }

    private int onObjectEndHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        byteBuffer.put((byte) '}');
        remaining--;
        popState();
        return remaining;
    }

    private int onArrayEnd(@Nonnull ByteBuffer byteBuffer, int remaining) {
        if (remaining == 0) {
            state = State.ARRAY_END;
        } else {
            remaining = onArrayEndHR(byteBuffer, remaining);
        }
        return remaining;
    }

    private int onArrayEndHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        byteBuffer.put((byte) ']');
        remaining--;
        popState();
        return remaining;
    }

    private int onObjectCommaHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        byteBuffer.put((byte) ',');
        remaining--;
        return writeObjectFieldName(byteBuffer, remaining);
    }

    private int onArrayCommaHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        byteBuffer.put((byte) ',');
        remaining--;
        return writeArrayValue(byteBuffer, remaining);
    }

    private int onObjectColonHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        byteBuffer.put((byte) ':');
        remaining--;
        final var value = entryValue;
        entryValue = null;
        jsonObjectIterators.push(jsonObjectIterator);
        states.push(State.OBJECT_COMMA);
        return write(byteBuffer, remaining, value);
    }

    private int onObjectColonLastHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        byteBuffer.put((byte) ':');
        remaining--;
        final var value = entryValue;
        entryValue = null;
        states.push(State.OBJECT_END);
        return write(byteBuffer, remaining, value);
    }

    private int writeArrayValue(@Nonnull ByteBuffer byteBuffer, int remaining) {
        final var value = jsonArrayIterator.next();
        if (jsonArrayIterator.hasNext()) {
            jsonArrayIterators.push(jsonArrayIterator);
            states.push(State.ARRAY_COMMA);
        } else {
            jsonArrayIterator = null;
            states.push(State.ARRAY_END);
        }
        return write(byteBuffer, remaining, value);
    }

    private static final long DOUBLE_EXP_BIT_MASK = 0x7FF0000000000000L;
    private static final int EXP_BIT_MASK = 0x7F800000;

    private int write(@Nonnull ByteBuffer byteBuffer, int remaining, @Nullable Object o) {

        if (o == null) {
            return onBytes(byteBuffer, remaining, NULL_BYTES);
        } else if (o instanceof final String value) {
            return onQuotedStringStart(byteBuffer, remaining, value);
        } else if (o instanceof final Number n) {
            if (n instanceof final Long value) {
                builder.setLength(0);
                builder.append((long) value);
            } else if (n instanceof final Integer value) {
                builder.setLength(0);
                builder.append((int) value);
            } else if (n instanceof final Short value) {
                builder.setLength(0);
                builder.append((short) value);
            } else if (n instanceof final Byte value) {
                builder.setLength(0);
                builder.append((byte) value);
            } else if (n instanceof final BigDecimal value) {
                builder.setLength(0);
                builder.append(value);
            } else if (n instanceof final BigInteger value) {
                builder.setLength(0);
                builder.append(value);
            } else if (n instanceof final Double value) {
                if ((doubleToRawLongBits(value) & DOUBLE_EXP_BIT_MASK) == DOUBLE_EXP_BIT_MASK) {
                    throw new IllegalArgumentException();// Infinity or NaN
                }
                builder.setLength(0);
                builder.append((double) value);
            } else if (n instanceof final Float value) {
                if ((floatToRawIntBits(value) & EXP_BIT_MASK) == EXP_BIT_MASK) {
                    throw new IllegalArgumentException();// Infinity or NaN
                }
                builder.setLength(0);
                builder.append((float) value);
            } else {
                throw new IllegalArgumentException();
            }
            if (remaining == 0) {
                stringPosition = remaining;
                state = State.BUILDER;
                return remaining;
            } else {
                final var length = builder.length();
                if (length > remaining) {
                    for (int i = 0; i < remaining; i++) {
                        byteBuffer.put((byte) builder.charAt(i));
                    }
                    stringPosition = remaining;
                    state = State.BUILDER;
                    return 0;
                } else {
                    remaining -= length;
                    for (int i = 0; i < length; i++) {
                        byteBuffer.put((byte) builder.charAt(i));
                    }
                    popState();
                    return remaining;
                }
            }
        } else if (o instanceof final Boolean value) {
            return onBytes(byteBuffer, remaining, value ? TRUE_BYTES : FALSE_BYTES);
        } else if (o instanceof final JsonObject value) {
            jsonObjectIterator = value.isEmpty() ? null : value.iterator();
            if (remaining == 0) {
                state = State.OBJECT_START;
                return remaining;
            } else {
                return onObjectStartHR(byteBuffer, remaining);
            }
        } else if (o instanceof final JsonArray value) {
            this.jsonArrayIterator = value.isEmpty() ? null : value.iterator();
            if (remaining == 0) {
                state = State.ARRAY_START;
                return remaining;
            } else {
                return onArrayStartHR(byteBuffer, remaining);
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    private int onBuilderHRS(@Nonnull ByteBuffer byteBuffer, int remaining) {
        final var length = builder.length();
        final var position = stringPosition;
        final var r = length - position;
        if (r > remaining) {
            final var end = position + remaining;
            for (int i = position; i < end; i++) {
                byteBuffer.put((byte) builder.charAt(i));
            }
            stringPosition = end;
            return 0;
        } else {
            remaining -= r;
            for (int i = position; i < length; i++) {
                byteBuffer.put((byte) builder.charAt(i));
            }
            popState();
            return remaining;
        }
    }

    private int onBytes(@Nonnull ByteBuffer byteBuffer, int remaining, byte[] bytes) {
        if (remaining == 0) {
            bytesValue = bytes;
            bytesPosition = 0;
            state = State.BYTES;
            return 0;
        } else {
            final var length = bytes.length;
            if (length > remaining) {
                byteBuffer.put(bytes, 0, remaining);
                bytesValue = bytes;
                bytesPosition = remaining;
                state = State.BYTES;
                return 0;
            } else {
                byteBuffer.put(bytes);
                remaining -= length;
                popState();
                return remaining;
            }
        }
    }

    private int onBytesHRS(@Nonnull ByteBuffer byteBuffer, int remaining) {
        final var bytes = bytesValue;
        final var length = bytes.length;
        final var position = bytesPosition;
        final var r = length - position;
        if (r > remaining) {
            byteBuffer.put(bytes, position, remaining);
            bytesPosition = position + remaining;
            return 0;
        } else {
            byteBuffer.put(bytes, position, r);
            remaining -= r;
            popState();
            return remaining;
        }
    }

    private int writeObjectFieldName(@Nonnull ByteBuffer byteBuffer, int remaining) {
        final var entry = jsonObjectIterator.next();
        if (jsonObjectIterator.hasNext()) {
            jsonObjectIterators.push(jsonObjectIterator);
            states.push(State.OBJECT_COLON);
        } else {
            jsonObjectIterator = null;
            states.push(State.OBJECT_COLON_LAST);
        }
        entryValue = entry.getValue();
        return onQuotedStringStart(byteBuffer, remaining, entry.getKey());
    }

    private int onQuotedStringStart(@Nonnull ByteBuffer byteBuffer, int remaining, @Nonnull String value) {
        if (remaining == 0) {
            stringValue = value.isEmpty() ? null : value;
            state = State.QUOTED_STRING_START;
        } else {
            byteBuffer.put((byte) '"');
            remaining--;
            if (value.isEmpty()) {
                remaining = onQuotedStringEnd(byteBuffer, remaining);
            } else {
                remaining = onQuotedString(byteBuffer, remaining, value);
            }
        }
        return remaining;
    }

    private int onQuotedStringStartHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        byteBuffer.put((byte) '"');
        remaining--;
        final var value = this.stringValue;
        if (value == null) {
            return onQuotedStringEnd(byteBuffer, remaining);
        } else {
            return onQuotedString(byteBuffer, remaining, value);
        }
    }

    private int onQuotedStringEnd(@Nonnull ByteBuffer byteBuffer, int remaining) {
        if (remaining == 0) {
            state = State.QUOTED_STRING_END;
        } else {
            remaining = onQuotedStringEndHR(byteBuffer, remaining);
        }
        return remaining;
    }

    private int onQuotedStringEndHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        byteBuffer.put((byte) '"');
        remaining--;
        popState();
        return remaining;
    }

    private int onQuotedString(@Nonnull ByteBuffer byteBuffer, int remaining, @Nonnull String value) {
        if (remaining == 0) {
            stringValue = value;
            stringPosition = 0;
            state = State.QUOTED_STRING;
            return remaining;
        } else {
            return onQuotedStringHR(byteBuffer, remaining, value, 0);
        }
    }

    private int onQuotedString(@Nonnull ByteBuffer byteBuffer, int remaining) {
        if (remaining == 0) {
            state = State.QUOTED_STRING;
            return remaining;
        } else {
            return onQuotedStringHR(byteBuffer, remaining);
        }
    }

    private int onQuotedStringBytesHRS(@Nonnull ByteBuffer byteBuffer, int remaining) {
        final var bytes = bytesValue;
        final var length = bytes.length;
        final var position = bytesPosition;
        final var r = length - position;
        if (r > remaining) {
            byteBuffer.put(bytes, position, remaining);
            bytesPosition = position + remaining;
            return 0;
        } else {
            byteBuffer.put(bytes, position, r);
            remaining -= r;
            if (stringPosition == stringValue.length()) {
                stringValue = null;
                return onQuotedStringEnd(byteBuffer, remaining);
            } else {
                return onQuotedString(byteBuffer, remaining);
            }
        }
    }

    private int onQuotedStringHR(@Nonnull ByteBuffer byteBuffer, int remaining) {
        return onQuotedStringHR(byteBuffer, remaining, stringValue, stringPosition);
    }

    private int onQuotedStringHR(@Nonnull ByteBuffer byteBuffer, int remaining, @Nonnull String value, int position) {
        final var valueLength = value.length();
        while (true) {
            final var c = value.charAt(position);
            position++;
            final byte[] bytes;
            if (c < 32) {
                bytes = SPECIALS[c];
            } else {
                switch (c) {
                    case '"' -> bytes = DOUBLE_QUOTE;
                    case '\\' -> bytes = REVERSE_SOLIDUS;
                    default -> {
                        if (c < 0x80) {//7bit->1byte
                            byteBuffer.put((byte) c);
                            remaining--;
                            if (remaining == 0) {
                                return lastCharInBuffer(value, position);
                            }
                        } else if (c < 0x800) {//11bit->2bytes
                            if (remaining < 2) {
                                byteBuffer.put((byte) (0xc0 | (c >> 6)));
                                remainedBytes[2] = (byte) (0x80 | (c & 0x3f));
                                return bytesRemained(remainedBytes, 2, value, position);
                            } else {
                                byteBuffer.put((byte) (0xc0 | (c >> 6)));
                                byteBuffer.put((byte) (0x80 | (c & 0x3f)));
                                remaining -= 2;
                                if (remaining == 0) {
                                    return lastCharInBuffer(value, position);
                                }
                            }
                        } else if (c < '\uD800' || c > '\uDFFF') {//16bit->3bytes
                            switch (remaining) {
                                case 1 -> {
                                    byteBuffer.put((byte) (0xe0 | ((c >> 12))));
                                    remainedBytes[1] = (byte) (0x80 | ((c >> 6) & 0x3f));
                                    remainedBytes[2] = (byte) (0x80 | (c & 0x3f));
                                    return bytesRemained(remainedBytes, 1, value, position);
                                }
                                case 2 -> {
                                    byteBuffer.put((byte) (0xe0 | ((c >> 12))));
                                    byteBuffer.put((byte) (0x80 | ((c >> 6) & 0x3f)));
                                    remainedBytes[2] = (byte) (0x80 | (c & 0x3f));
                                    return bytesRemained(remainedBytes, 2, value, position);
                                }
                                default -> {
                                    byteBuffer.put((byte) (0xe0 | ((c >> 12))));
                                    byteBuffer.put((byte) (0x80 | ((c >> 6) & 0x3f)));
                                    byteBuffer.put((byte) (0x80 | (c & 0x3f)));
                                    remaining -= 3;
                                    if (remaining == 0) {
                                        return lastCharInBuffer(value, position);
                                    }
                                }
                            }
                        } else {//4bytes, surrogate pair
                            if (c > '\uDBFF') {
                                throw new IllegalArgumentException();
                            }
                            final var c2 = value.charAt(position++);
                            if (c2 < '\uDC00' || c2 > '\uDFFF') {
                                throw new IllegalArgumentException();
                            }
                            final var uc = Character.toCodePoint(c, c2);
                            switch (remaining) {
                                case 1 -> {
                                    byteBuffer.put((byte) (0xf0 | ((uc >> 18))));
                                    remainedBytes[0] = (byte) (0x80 | ((uc >> 12) & 0x3f));
                                    remainedBytes[1] = (byte) (0x80 | ((uc >> 6) & 0x3f));
                                    remainedBytes[2] = (byte) (0x80 | (uc & 0x3f));
                                    return bytesRemained(remainedBytes, 0, value, position);
                                }
                                case 2 -> {
                                    byteBuffer.put((byte) (0xf0 | ((uc >> 18))));
                                    byteBuffer.put((byte) (0x80 | ((uc >> 12) & 0x3f)));
                                    remainedBytes[1] = (byte) (0x80 | ((uc >> 6) & 0x3f));
                                    remainedBytes[2] = (byte) (0x80 | (uc & 0x3f));
                                    return bytesRemained(remainedBytes, 1, value, position);
                                }
                                case 3 -> {
                                    byteBuffer.put((byte) (0xf0 | ((uc >> 18))));
                                    byteBuffer.put((byte) (0x80 | ((uc >> 12) & 0x3f)));
                                    byteBuffer.put((byte) (0x80 | ((uc >> 6) & 0x3f)));
                                    remainedBytes[2] = (byte) (0x80 | (uc & 0x3f));
                                    return bytesRemained(remainedBytes, 2, value, position);
                                }
                                default -> {
                                    byteBuffer.put((byte) (0xf0 | ((uc >> 18))));
                                    byteBuffer.put((byte) (0x80 | ((uc >> 12) & 0x3f)));
                                    byteBuffer.put((byte) (0x80 | ((uc >> 6) & 0x3f)));
                                    byteBuffer.put((byte) (0x80 | (uc & 0x3f)));
                                    remaining -= 4;
                                    if (remaining == 0) {
                                        return lastCharInBuffer(value, position);
                                    }
                                }
                            }
                        }
                        if (position == valueLength) {
                            return stringEnded(remaining);
                        }
                        continue;
                    }
                }
            }
            final var length = bytes.length;
            if (length > remaining) {
                byteBuffer.put(bytes, 0, remaining);
                return bytesRemained(bytes, remaining, value, position);
            } else {
                byteBuffer.put(bytes);
                remaining -= length;
                if (remaining == 0) {
                    return lastCharInBuffer(value, position);
                }
                if (position == valueLength) {
                    return stringEnded(remaining);
                }
            }
        }
    }

    private int stringEnded(int remaining) {
        stringValue = null;
        state = State.QUOTED_STRING_END;
        return remaining;
    }

    private int bytesRemained(@Nonnull byte[] bytes, int bp, @Nonnull String value, int sp) {
        bytesValue = bytes;
        bytesPosition = bp;
        stringValue = value;
        stringPosition = sp;
        state = State.QUOTED_STRING_BYTES;
        return 0;
    }

    private int lastCharInBuffer(@Nonnull String value, int position) {
        if (position == value.length()) {
            stringValue = null;
            state = State.QUOTED_STRING_END;
        } else {
            stringValue = value;
            stringPosition = position;
            state = State.QUOTED_STRING;
        }
        return 0;
    }
}
