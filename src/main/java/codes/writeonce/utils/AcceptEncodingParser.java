package codes.writeonce.utils;

import io.netty.handler.codec.http.HttpRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT_ENCODING;

public class AcceptEncodingParser {

    private static final char[] GZIP_ENCODING = "gzip".toCharArray();

    private static final int GZIP_ENCODING_LENGTH = GZIP_ENCODING.length;

    private int gzipQvalue;

    private int defaultQvalue;

    public boolean isGzipAllowed(@Nonnull HttpRequest request) {
        return isGzipAllowed(request.headers().get(ACCEPT_ENCODING));
    }

    boolean isGzipAllowed(@Nullable String acceptEncoding) {

        if (acceptEncoding == null) {
            return true;
        }

        gzipQvalue = -1;
        defaultQvalue = -1;

        var start = 0;
        while (true) {
            final var end = acceptEncoding.indexOf(',', start);
            if (end == -1) {
                matches(acceptEncoding, start, acceptEncoding.length());
                return gzipQvalue == -1 ? defaultQvalue == 1 : gzipQvalue == 1;
            } else {
                matches(acceptEncoding, start, end);
                start = end + 1;
            }
        }
    }

    private void matches(@Nonnull String acceptEncoding, int start, int end) {

        while (true) {
            if (start == end) {
                return;
            }
            final var c = acceptEncoding.charAt(start);
            switch (c) {
                case ';':
                    // unexpected garbage encountered
                    return;
                case ' ', '\t', '\r', '\n':
                    break;
                default:
                    matchCoding(acceptEncoding, start, end, c);
                    return;
            }
            start++;
        }
    }

    private void matchCoding(@Nonnull String acceptEncoding, int start, int end, char c) {

        if (c == '*') {
            defaultQvalue = matchSemicolon(acceptEncoding, start + 1, end) ? 1 : 0;
            return;
        }

        var j = 0;

        while (true) {
            if (GZIP_ENCODING[j] != Character.toLowerCase(c)) {
                return;
            }
            start++;
            j++;
            if (start == end) {
                if (j == GZIP_ENCODING_LENGTH) {
                    gzipQvalue = 1;
                }
                return;
            }
            c = acceptEncoding.charAt(start);
            //noinspection EnhancedSwitchMigration
            switch (c) {
                case ';':
                    if (j == GZIP_ENCODING_LENGTH) {
                        gzipQvalue = matchQValue(acceptEncoding, start + 1, end) ? 1 : 0;
                    }
                    return;
                case ' ', '\t', '\r', '\n':
                    if (j == GZIP_ENCODING_LENGTH) {
                        gzipQvalue = matchSemicolon(acceptEncoding, start + 1, end) ? 1 : 0;
                    }
                    return;
            }
            if (j == GZIP_ENCODING_LENGTH) {
                return;
            }
        }
    }

    private static boolean matchSemicolon(@Nonnull String acceptEncoding, int start, int end) {

        while (true) {
            if (start == end) {
                // no qvalue
                return true;
            }
            final var c = acceptEncoding.charAt(start);
            switch (c) {
                case ';':
                    return matchQValue(acceptEncoding, start + 1, end);
                case ' ', '\t', '\r', '\n':
                    break;
                default:
                    // unexpected garbage encountered
                    return false;
            }
            start++;
        }
    }

    private static boolean matchQValue(@Nonnull String acceptEncoding, int start, int end) {

        while (true) {
            if (start == end) {
                // incorrect qvalue
                return false;
            }
            final var c = acceptEncoding.charAt(start);
            switch (c) {
                case 'q':
                    return matchEq(acceptEncoding, start + 1, end);
                case ' ', '\t', '\r', '\n':
                    break;
                default:
                    // unexpected garbage encountered
                    return false;
            }
            start++;
        }
    }

    private static boolean matchEq(@Nonnull String acceptEncoding, int start, int end) {

        while (true) {
            if (start == end) {
                // incorrect qvalue
                return false;
            }
            final var c = acceptEncoding.charAt(start);
            switch (c) {
                case '=':
                    try {
                        return Float.parseFloat(acceptEncoding.substring(start + 1, end)) > 0;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                case ' ', '\t', '\r', '\n':
                    break;
                default:
                    // unexpected garbage encountered
                    return false;
            }
            start++;
        }
    }
}
