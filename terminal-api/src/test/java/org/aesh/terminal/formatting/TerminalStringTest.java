package org.aesh.terminal.formatting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.aesh.terminal.utils.ANSI;
import org.junit.Test;

public class TerminalStringTest {

    @Test
    public void testTerminalString() {
        TerminalString string = new TerminalString("foo");

        assertFalse(string.containSpaces());
        assertFalse(string.isFormatted());
        assertEquals("foo", string.getCharacters());

        string = new TerminalString("foo bar", new TerminalColor(Color.BLACK, Color.WHITE));
        assertTrue(string.containSpaces());
        assertTrue(string.isFormatted());
        assertEquals("foo bar", string.getCharacters());
        string.switchSpacesToEscapedSpaces();
        assertEquals("foo\\ bar", string.getCharacters());
        assertEquals(ANSI.START + ";30;47mfoo\\ bar" + ANSI.RESET, string.toString());

        string = new TerminalString("foo bar", true);
        assertTrue(string.containSpaces());
        assertFalse(string.isFormatted());
        assertEquals("foo bar", string.getCharacters());

        assertEquals(0, string.getANSILength());
    }

    @Test
    public void testHyperlinkConstructor() {
        String url = "https://example.com";
        TerminalString ts = new TerminalString("click me", url,
                new TerminalColor(Color.BLUE, Color.DEFAULT), new TerminalTextStyle());

        assertEquals("click me", ts.getCharacters());
        assertEquals(url, ts.getHyperlinkUrl());
    }

    @Test
    public void testHyperlinkToString() {
        String url = "https://example.com";
        TerminalString ts = new TerminalString("click me", url,
                new TerminalColor(), new TerminalTextStyle());

        String result = ts.toString();
        String expectedStart = ANSI.buildHyperlinkStart(url, null);
        String expectedEnd = ANSI.buildHyperlinkEnd();

        assertTrue("toString should start with hyperlink open",
                result.startsWith(expectedStart));
        assertTrue("toString should end with hyperlink close",
                result.endsWith(expectedEnd));
        assertTrue("toString should contain the text",
                result.contains("click me"));
    }

    @Test
    public void testNoHyperlinkUrl() {
        TerminalString ts = new TerminalString("plain text");
        assertNull("Default hyperlinkUrl should be null", ts.getHyperlinkUrl());

        String result = ts.toString();
        assertFalse("No hyperlink codes in output",
                result.contains(ANSI.buildHyperlinkEnd()));
    }

    @Test
    public void testSetHyperlinkUrl() {
        TerminalString ts = new TerminalString("text");
        assertNull(ts.getHyperlinkUrl());

        ts.setHyperlinkUrl("https://example.com");
        assertEquals("https://example.com", ts.getHyperlinkUrl());

        String result = ts.toString();
        assertTrue("After setting URL, toString should include hyperlink",
                result.contains(ANSI.buildHyperlinkStart("https://example.com", null)));
    }

    @Test
    public void testHyperlinkANSILength() {
        TerminalString plain = new TerminalString("text");
        int plainLength = plain.getANSILength();

        TerminalString linked = new TerminalString("text", "https://example.com",
                new TerminalColor(), new TerminalTextStyle());
        int linkedLength = linked.getANSILength();

        assertTrue("Hyperlinked ANSI length should be greater than plain",
                linkedLength > plainLength);

        int hyperlinkOverhead = ANSI.buildHyperlinkStart("https://example.com", null).length()
                + ANSI.buildHyperlinkEnd().length();
        assertEquals("Hyperlink should add exact overhead",
                plainLength + hyperlinkOverhead, linkedLength);
    }

    @Test
    public void testCloneRenderingAttributesPreservesHyperlink() {
        String url = "https://example.com";
        TerminalString original = new TerminalString("original", url,
                new TerminalColor(Color.RED, Color.DEFAULT), new TerminalTextStyle());

        TerminalString cloned = original.cloneRenderingAttributes("cloned");

        assertEquals("Cloned should have different text", "cloned", cloned.getCharacters());
        assertEquals("Cloned should preserve hyperlink URL", url, cloned.getHyperlinkUrl());
    }

    @Test
    public void testIgnoreRenderingWithHyperlink() {
        TerminalString ts = new TerminalString("text", true);
        ts.setHyperlinkUrl("https://example.com");

        // When ignoreRendering is true, toString should return plain text
        assertEquals("text", ts.toString());
        assertEquals(0, ts.getANSILength());
    }
}
