package codes.writeonce.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ETagMatcherTest {

    @Test
    public void test1() {
        final var matcher = new ETagMatcher("qwe").accept("\t \r\nW/\"q\\we\"\n\r \t").end();
        assertFalse(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test2() {
        final var matcher = new ETagMatcher("qwe").accept("\"q\\we\"").end();
        assertFalse(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test3() {
        final var matcher = new ETagMatcher("qwe").accept("\"q\\we\",W/\"q\\we\"").end();
        assertFalse(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test4() {
        final var matcher = new ETagMatcher("qwe").accept("W/\"q\\we\",\"q\\we\"").end();
        assertFalse(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test5() {
        final var matcher = new ETagMatcher("qwe").accept("\"qw\\e\",W/\"rty\"").end();
        assertFalse(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test6() {
        final var matcher = new ETagMatcher("qwe").accept("W/\"qw\\e\",\"rty\"").end();
        assertFalse(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test7() {
        final var matcher = new ETagMatcher("qwe").accept("\"rty\",W/\"qw\\e\"").end();
        assertFalse(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test8() {
        final var matcher = new ETagMatcher("qwe").accept("W/\"rty\",\"qw\\e\"").end();
        assertFalse(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test9() {
        final var matcher = new ETagMatcher("qwe").accept("W/\"rty\",\"qwe").end();
        assertTrue(matcher.isError());
        assertFalse(matcher.matches());
    }

    @Test
    public void test10() {
        final var matcher = new ETagMatcher("qwe").accept("W/\"qwe\",\"rty").end();
        assertTrue(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test11() {
        final var matcher = new ETagMatcher("qwe").accept("W/\"rty\",").end();
        assertTrue(matcher.isError());
        assertFalse(matcher.matches());
    }

    @Test
    public void test12() {
        final var matcher = new ETagMatcher("qwe").accept("W/\"qwe\",").end();
        assertTrue(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test13() {
        final var matcher = new ETagMatcher("qwe").accept("W/\"rty").end();
        assertTrue(matcher.isError());
        assertFalse(matcher.matches());
    }

    @Test
    public void test14() {
        final var matcher = new ETagMatcher("qwe").accept("W/\"qwe").end();
        assertTrue(matcher.isError());
        assertFalse(matcher.matches());
    }

    @Test
    public void test15() {
        final var matcher = new ETagMatcher("qwe").accept("W/\"").end();
        assertTrue(matcher.isError());
        assertFalse(matcher.matches());
    }

    @Test
    public void test16() {
        final var matcher = new ETagMatcher("qwe").accept("W/").end();
        assertTrue(matcher.isError());
        assertFalse(matcher.matches());
    }

    @Test
    public void test17() {
        final var matcher = new ETagMatcher("qwe").accept("W").end();
        assertTrue(matcher.isError());
        assertFalse(matcher.matches());
    }

    @Test
    public void test18() {
        final var matcher = new ETagMatcher("qwe").accept("").end();
        assertTrue(matcher.isError());
        assertFalse(matcher.matches());
    }

    @Test
    public void test19() {
        final var matcher = new ETagMatcher("qwe").accept("\r \n\t").end();
        assertTrue(matcher.isError());
        assertFalse(matcher.matches());
    }

    @Test
    public void test20() {
        final var matcher = new ETagMatcher("qwe").accept("\r \n\t*\r \n\t").end();
        assertFalse(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test21() {
        final var matcher = new ETagMatcher("qwe").accept("\r \n\t*\r \n\tW/\"rty\"").end();
        assertTrue(matcher.isError());
        assertTrue(matcher.matches());
    }

    @Test
    public void test22() {
        final var matcher = new ETagMatcher("qwe").accept("\r \n\t*\r \n\t\"rty\"").end();
        assertTrue(matcher.isError());
        assertTrue(matcher.matches());
    }
}
