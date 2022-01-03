package codes.writeonce.utils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;

public class JsonPushParser {

    private final JsonFactory jsonFactory = new JsonFactory();
    private final ArrayDeque<JsonObject> jsonObjects = new ArrayDeque<>();
    private final ArrayDeque<JsonArray> jsonArrays = new ArrayDeque<>();
    private final ArrayDeque<String> fieldNames = new ArrayDeque<>();
    private final ArrayDeque<State> states = new ArrayDeque<>();
    private JsonObject jsonObject;
    private JsonArray jsonArray;
    private String fieldName = null;
    private State state = State.INIT;
    private JsonParser parser;
    private ByteArrayFeeder feeder;

    public void start() {

        try {
            parser = jsonFactory.createNonBlockingByteArrayParser();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        feeder = (ByteArrayFeeder) parser.getNonBlockingInputFeeder();
    }

    public void feedInput(byte[] buf, int start, int end) {

        try {
            feeder.feedInput(buf, start, end);
            while (!feeder.needMoreInput()) {
                processState();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void endOfInput() {

        if (state != State.END) {
            throw new IllegalArgumentException();
        }
        feeder.endOfInput();
        try {
            processState();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void processState() throws IOException {

        switch (state) {
            case INIT -> {
                final var token = parser.nextToken();
                switch (token) {
                    case NOT_AVAILABLE -> {
                    }
                    case START_ARRAY -> {
                        state = State.ARRAY;
                        jsonArray = new JsonArray();
                        states.push(State.END);
                    }
                    case START_OBJECT -> {
                        state = State.OBJECT;
                        jsonObject = new JsonObject();
                        states.push(State.END);
                    }
                    default -> throw new IllegalArgumentException();
                }
            }
            case FIELD -> {
                final var token = parser.nextToken();
                switch (token) {
                    case NOT_AVAILABLE -> {
                    }
                    case START_ARRAY -> {
                        jsonObjects.push(jsonObject);
                        fieldNames.push(fieldName);
                        states.push(state);
                        fieldName = null;
                        jsonObject = null;
                        state = State.ARRAY;
                        jsonArray = new JsonArray();
                    }
                    case START_OBJECT -> {
                        jsonObjects.push(jsonObject);
                        fieldNames.push(fieldName);
                        states.push(state);
                        fieldName = null;
                        state = State.OBJECT;
                        jsonObject = new JsonObject();
                    }
                    case VALUE_NULL -> {
                        jsonObject.putNull(fieldName);
                        fieldName = null;
                        state = State.OBJECT;
                    }
                    case VALUE_FALSE -> {
                        jsonObject.put(fieldName, false);
                        fieldName = null;
                        state = State.OBJECT;
                    }
                    case VALUE_TRUE -> {
                        jsonObject.put(fieldName, true);
                        fieldName = null;
                        state = State.OBJECT;
                    }
                    case VALUE_NUMBER_INT -> {
                        jsonObject.put(fieldName, parser.getLongValue());
                        fieldName = null;
                        state = State.OBJECT;
                    }
                    case VALUE_NUMBER_FLOAT -> {
                        jsonObject.put(fieldName, parser.getDecimalValue().toString());
                        fieldName = null;
                        state = State.OBJECT;
                    }
                    case VALUE_STRING -> {
                        jsonObject.put(fieldName, parser.getText());
                        fieldName = null;
                        state = State.OBJECT;
                    }
                    default -> throw new IllegalArgumentException();
                }
            }
            case OBJECT -> {
                final var token = parser.nextToken();
                switch (token) {
                    case NOT_AVAILABLE -> {
                    }
                    case END_OBJECT -> {
                        state = states.pop();
                        switch (state) {
                            case END -> {
                            }
                            case FIELD -> {
                                final var entry = jsonObjects.pop();
                                entry.put(fieldNames.pop(), jsonObject);
                                jsonObject = entry;
                                state = State.OBJECT;
                            }
                            case ARRAY -> {
                                jsonArray = jsonArrays.pop();
                                jsonArray.add(jsonObject);
                                jsonObject = null;
                            }
                            default -> throw new IllegalArgumentException();
                        }
                    }
                    case FIELD_NAME -> {
                        fieldName = parser.getCurrentName();
                        state = State.FIELD;
                    }
                    default -> throw new IllegalArgumentException();
                }
            }
            case ARRAY -> {
                final var token = parser.nextToken();
                switch (token) {
                    case NOT_AVAILABLE -> {
                    }
                    case START_ARRAY -> {
                        jsonArrays.push(jsonArray);
                        states.push(state);
                        jsonArray = new JsonArray();
                    }
                    case START_OBJECT -> {
                        jsonArrays.push(jsonArray);
                        states.push(state);
                        state = State.OBJECT;
                        jsonObject = new JsonObject();
                        jsonArray = null;
                    }
                    case END_ARRAY -> {
                        state = states.pop();
                        switch (state) {
                            case END -> {
                            }
                            case FIELD -> {
                                jsonObject = jsonObjects.pop();
                                jsonObject.put(fieldNames.pop(), jsonArray);
                                jsonArray = null;
                                state = State.OBJECT;
                            }
                            case ARRAY -> {
                                final var entry = jsonArrays.pop();
                                entry.add(jsonArray);
                                jsonArray = entry;
                            }
                            default -> throw new IllegalArgumentException();
                        }
                    }
                    case VALUE_NULL -> jsonArray.addNull();
                    case VALUE_FALSE -> jsonArray.add(false);
                    case VALUE_TRUE -> jsonArray.add(true);
                    case VALUE_NUMBER_INT -> jsonArray.add(parser.getLongValue());
                    case VALUE_NUMBER_FLOAT -> jsonArray.add(parser.getDecimalValue().toString());
                    case VALUE_STRING -> jsonArray.add(parser.getText());
                    default -> throw new IllegalArgumentException();
                }
            }
            case END -> {
                final var token = parser.nextToken();
                if (token != null) {
                    throw new IllegalArgumentException();
                }
            }
            default -> throw new IllegalArgumentException();
        }
    }

    public void cleanup() {

        try {
            parser.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        parser = null;
        feeder = null;
        jsonObjects.clear();
        jsonArrays.clear();
        states.clear();
        jsonObject = null;
        jsonArray = null;
        fieldName = null;
        state = State.INIT;
    }

    @Nullable
    public JsonObject getJsonObject() {
        return jsonObject;
    }

    @Nullable
    public JsonArray getJsonArray() {
        return jsonArray;
    }

    private enum State {
        INIT,
        OBJECT,
        ARRAY,
        FIELD,
        END
    }
}
