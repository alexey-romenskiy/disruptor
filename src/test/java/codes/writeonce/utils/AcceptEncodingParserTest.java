package codes.writeonce.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AcceptEncodingParserTest {

    @Test
    public void isGzipAllowed() {
        assertTrue(new AcceptEncodingParser().isGzipAllowed((String) null));
        assertFalse(new AcceptEncodingParser().isGzipAllowed(""));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("*"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("gzip"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("gzip;q=1"));
        assertFalse(new AcceptEncodingParser().isGzipAllowed("gzip;q=0"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("gzip;q=1, *;q=0"));
        assertFalse(new AcceptEncodingParser().isGzipAllowed("gzip;q=0, *;q=0"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("gzip;q=1, *;q=1"));
        assertFalse(new AcceptEncodingParser().isGzipAllowed("gzip;q=0, *;q=1"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("*;q=1"));
        assertFalse(new AcceptEncodingParser().isGzipAllowed("*;q=0"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("*;q=0, gzip;q=1"));
        assertFalse(new AcceptEncodingParser().isGzipAllowed("*;q=0, gzip;q=0"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("*;q=1, gzip;q=1"));
        assertFalse(new AcceptEncodingParser().isGzipAllowed("*;q=1, gzip;q=0"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("compress, gzip"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("compress;q=0.5, gzip;q=1.0"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("gzip;q=1.0, identity; q=0.5, *;q=0"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed(" gzip ; q = 1.0 , identity ; q = 0.5 , * ; q = 0 "));
        assertTrue(new AcceptEncodingParser().isGzipAllowed("gzip;q=1.0,identity;q=0.5,*;q=0"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed(",,gzip,,"));
        assertTrue(new AcceptEncodingParser().isGzipAllowed(" , , gzip , , "));
    }
}
