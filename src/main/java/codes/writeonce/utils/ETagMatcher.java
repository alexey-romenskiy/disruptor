package codes.writeonce.utils;

import javax.annotation.Nonnull;

public class ETagMatcher {

    private static final int STATE_INIT = 0;
    private static final int STATE_END = 1;
    private static final int STATE_ERROR = 2;
    private static final int STATE_W = 3;
    private static final int STATE_W_SLASH = 4;
    private static final int STATE_QUOTE = 5;
    private static final int STATE_QUOTE_END = 6;
    private static final int STATE_QUOTE_ESCAPE = 7;
    private static final int STATE_COMMA = 8;
    private static final int STATE_QUOTE_ESCAPE_SKIP = 9;
    private static final int STATE_QUOTE_SKIP = 10;
    private static final int STATE_ASTERISK = 11;

    @Nonnull
    private final String etag;

    private int state;

    private int position;

    private boolean matches;

    public ETagMatcher(@Nonnull String etag) {
        this.etag = etag;
        this.state = STATE_INIT;
    }

    @Nonnull
    public ETagMatcher accept(@Nonnull String ifNoneMatch) {

        var i = 0;
        final var end = ifNoneMatch.length();

        while (i != end) {
            switch (state) {
                case STATE_INIT -> i = parseInit(ifNoneMatch, i, end);
                case STATE_W -> i = parseW(ifNoneMatch, i, end);
                case STATE_W_SLASH -> i = parseWSlash(ifNoneMatch, i, end);
                case STATE_QUOTE -> i = parseQuote(ifNoneMatch, i, end, position);
                case STATE_QUOTE_ESCAPE -> i = parseQuoteEscape(ifNoneMatch, i, end, position);
                case STATE_QUOTE_END -> i = parseQuoteEnd(ifNoneMatch, i, end);
                case STATE_COMMA -> i = parseComma(ifNoneMatch, i, end);
                case STATE_QUOTE_SKIP -> i = parseQuoteSkip(ifNoneMatch, i, end);
                case STATE_QUOTE_ESCAPE_SKIP -> i = parseQuoteEscapeSkip(ifNoneMatch, i, end);
                case STATE_ASTERISK -> i = parseAsterisk(ifNoneMatch, i, end);
                case STATE_ERROR -> {
                    return this;
                }
                case STATE_END -> throw new IllegalStateException();
                default -> throw new IllegalStateException();
            }
        }

        return this;
    }

    @Nonnull
    public ETagMatcher reset() {
        state = STATE_INIT;
        matches = false;
        return this;
    }

    @Nonnull
    public ETagMatcher end() {

        switch (state) {
            case STATE_INIT, STATE_W, STATE_W_SLASH, STATE_QUOTE, STATE_QUOTE_ESCAPE, STATE_COMMA, STATE_QUOTE_SKIP, STATE_QUOTE_ESCAPE_SKIP ->
                    state = STATE_ERROR;
            case STATE_QUOTE_END, STATE_ASTERISK -> state = STATE_END;
            case STATE_ERROR -> {
                // empty
            }
            case STATE_END -> throw new IllegalStateException();
            default -> throw new IllegalStateException();
        }

        return this;
    }

    public boolean matches() {
        return matches;
    }

    public boolean isError() {
        return state == STATE_ERROR;
    }

    private int parseInit(@Nonnull String ifNoneMatch, int i, int end) {

        while (true) {
            final var c = ifNoneMatch.charAt(i);
            switch (c) {
                case ' ', '\r', '\n', '\t' -> {
                    i++;
                    if (i == end) {
                        return i;
                    }
                }
                case 'W' -> {
                    i++;
                    if (i == end) {
                        state = STATE_W;
                        return i;
                    }
                    return parseW(ifNoneMatch, i, end);
                }
                case '"' -> {
                    i++;
                    if (i == end) {
                        state = STATE_QUOTE;
                        position = 0;
                        return i;
                    }
                    return parseQuote(ifNoneMatch, i, end, 0);
                }
                case '*' -> {
                    i++;
                    state = STATE_ASTERISK;
                    matches = true;
                    return i;
                }
                default -> {
                    state = STATE_ERROR;
                    return i;
                }
            }
        }
    }

    private int parseAsterisk(@Nonnull String ifNoneMatch, int i, int end) {

        while (true) {
            final var c = ifNoneMatch.charAt(i);
            switch (c) {
                case ' ', '\r', '\n', '\t' -> {
                    i++;
                    if (i == end) {
                        return i;
                    }
                }
                default -> {
                    state = STATE_ERROR;
                    return i;
                }
            }
        }
    }

    private int parseComma(@Nonnull String ifNoneMatch, int i, int end) {

        while (true) {
            final var c = ifNoneMatch.charAt(i);
            switch (c) {
                case ' ', '\r', '\n', '\t' -> {
                    i++;
                    if (i == end) {
                        state = STATE_COMMA;
                        return i;
                    }
                }
                case 'W' -> {
                    i++;
                    if (i == end) {
                        state = STATE_W;
                        return i;
                    }
                    return parseW(ifNoneMatch, i, end);
                }
                case '"' -> {
                    i++;
                    if (i == end) {
                        if (matches) {
                            state = STATE_QUOTE_SKIP;
                        } else {
                            state = STATE_QUOTE;
                            position = 0;
                        }
                        return i;
                    }
                    if (matches) {
                        return parseQuoteSkip(ifNoneMatch, i, end);
                    } else {
                        return parseQuote(ifNoneMatch, i, end, 0);
                    }
                }
                default -> {
                    state = STATE_ERROR;
                    return i;
                }
            }
        }
    }

    private int parseQuoteEnd(@Nonnull String ifNoneMatch, int i, int end) {

        while (true) {
            final var c = ifNoneMatch.charAt(i);
            switch (c) {
                case ' ', '\r', '\n', '\t' -> {
                    i++;
                    if (i == end) {
                        state = STATE_QUOTE_END;
                        return i;
                    }
                }
                case ',' -> {
                    i++;
                    if (i == end) {
                        state = STATE_COMMA;
                        return i;
                    }
                    return parseComma(ifNoneMatch, i, end);
                }
                default -> {
                    state = STATE_ERROR;
                    return i;
                }
            }
        }
    }

    private int parseW(@Nonnull String ifNoneMatch, int i, int end) {

        if (ifNoneMatch.charAt(i) == '/') {
            i++;
            if (i == end) {
                state = STATE_W_SLASH;
                return i;
            }
            return parseWSlash(ifNoneMatch, i, end);
        } else {
            state = STATE_ERROR;
            return i;
        }
    }

    private int parseWSlash(@Nonnull String ifNoneMatch, int i, int end) {

        if (ifNoneMatch.charAt(i) == '"') {
            i++;
            if (i == end) {
                if (matches) {
                    state = STATE_QUOTE_SKIP;
                } else {
                    state = STATE_QUOTE;
                    position = 0;
                }
                return i;
            }
            if (matches) {
                return parseQuoteSkip(ifNoneMatch, i, end);
            } else {
                return parseQuote(ifNoneMatch, i, end, 0);
            }
        } else {
            state = STATE_ERROR;
            return i;
        }
    }

    private int parseQuote(@Nonnull String ifNoneMatch, int i, int end, int position) {

        while (true) {
            var c = ifNoneMatch.charAt(i++);
            switch (c) {
                case '\\' -> {
                    if (position == etag.length()) {
                        state = STATE_QUOTE_ESCAPE_SKIP;
                        return i;
                    } else {
                        if (i == end) {
                            state = STATE_QUOTE_ESCAPE;
                            this.position = position;
                            return i;
                        }
                        c = ifNoneMatch.charAt(i++);
                        if (c == etag.charAt(position)) {
                            position++;
                            if (i == end) {
                                state = STATE_QUOTE;
                                this.position = position;
                                return i;
                            }
                        } else {
                            state = STATE_QUOTE_SKIP;
                            return i;
                        }
                    }
                }
                case '"' -> {
                    if (position == etag.length()) {
                        matches = true;
                    }
                    state = STATE_QUOTE_END;
                    return i;
                }
                default -> {
                    if (position == etag.length()) {
                        state = STATE_QUOTE_SKIP;
                        return i;
                    } else {
                        if (c == etag.charAt(position)) {
                            position++;
                            if (i == end) {
                                state = STATE_QUOTE;
                                this.position = position;
                                return i;
                            }
                        } else {
                            state = STATE_QUOTE_SKIP;
                            return i;
                        }
                    }
                }
            }
        }
    }

    private int parseQuoteSkip(@Nonnull String ifNoneMatch, int i, int end) {

        while (true) {
            var c = ifNoneMatch.charAt(i++);
            switch (c) {
                case '\\' -> {
                    if (i == end) {
                        state = STATE_QUOTE_ESCAPE_SKIP;
                        return i;
                    }
                    i++;
                    if (i == end) {
                        state = STATE_QUOTE_SKIP;
                        return i;
                    }
                }
                case '"' -> {
                    state = STATE_QUOTE_END;
                    return i;
                }
                default -> {
                    if (i == end) {
                        state = STATE_QUOTE_SKIP;
                        return i;
                    }
                }
            }
        }
    }

    private int parseQuoteEscape(@Nonnull String ifNoneMatch, int i, int end, int position) {

        final var c = ifNoneMatch.charAt(i++);
        if (c == etag.charAt(position)) {
            position++;
            state = STATE_QUOTE;
            this.position = position;
        } else {
            state = STATE_QUOTE_SKIP;
        }
        return i;
    }

    private int parseQuoteEscapeSkip(@Nonnull String ifNoneMatch, int i, int end) {

        i++;
        state = STATE_QUOTE_SKIP;
        return i;
    }
}
