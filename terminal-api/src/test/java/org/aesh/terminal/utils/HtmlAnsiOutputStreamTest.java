/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.terminal.utils;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

public class HtmlAnsiOutputStreamTest {

    private String convert(String input) throws IOException {
        ByteArrayOutputStream html = new ByteArrayOutputStream();
        try (HtmlAnsiOutputStream out = new HtmlAnsiOutputStream(html)) {
            out.write(input.getBytes());
        }
        return html.toString();
    }

    @Test
    public void testPlainText() throws IOException {
        assertEquals("hello world", convert("hello world"));
    }

    @Test
    public void testHtmlEscaping() throws IOException {
        assertEquals("&lt;div class=&quot;foo&quot;&gt;a &amp; b&lt;/div&gt;",
                convert("<div class=\"foo\">a & b</div>"));
    }

    @Test
    public void testBold() throws IOException {
        assertEquals("<b>bold</b> normal",
                convert("\u001B[1mbold\u001B[0m normal"));
    }

    @Test
    public void testUnderline() throws IOException {
        assertEquals("<u>underlined</u> normal",
                convert("\u001B[4munderlined\u001B[0m normal"));
    }

    @Test
    public void testForegroundColor() throws IOException {
        assertEquals("<span style=\"color: red;\">red text</span>",
                convert("\u001B[31mred text\u001B[0m"));
    }

    @Test
    public void testBackgroundColor() throws IOException {
        assertEquals("<span style=\"background-color: green;\">green bg</span>",
                convert("\u001B[42mgreen bg\u001B[0m"));
    }

    @Test
    public void testBrightForegroundColor() throws IOException {
        assertEquals("<span style=\"color: cyan;\">bright cyan</span>",
                convert("\u001B[96mbright cyan\u001B[0m"));
    }

    @Test
    public void testBrightBackgroundColor() throws IOException {
        assertEquals("<span style=\"background-color: yellow;\">bright yellow bg</span>",
                convert("\u001B[103mbright yellow bg\u001B[0m"));
    }

    @Test
    public void testBoldAndColor() throws IOException {
        assertEquals("<b><span style=\"color: red;\">bold red</span></b>",
                convert("\u001B[1;31mbold red\u001B[0m"));
    }

    @Test
    public void test256Color() throws IOException {
        // Color index 196 = #ff0000
        assertEquals("<span style=\"color: #ff0000;\">256 color</span>",
                convert("\u001B[38;5;196m256 color\u001B[0m"));
    }

    @Test
    public void test256BackgroundColor() throws IOException {
        // Color index 21 = #0000ff
        assertEquals("<span style=\"background-color: #0000ff;\">256 bg</span>",
                convert("\u001B[48;5;21m256 bg\u001B[0m"));
    }

    @Test
    public void testTrueColor() throws IOException {
        assertEquals("<span style=\"color: #ff8000;\">true color</span>",
                convert("\u001B[38;2;255;128;0mtrue color\u001B[0m"));
    }

    @Test
    public void testTrueColorBackground() throws IOException {
        assertEquals("<span style=\"background-color: #204060;\">true color bg</span>",
                convert("\u001B[48;2;32;64;96mtrue color bg\u001B[0m"));
    }

    @Test
    public void testResetClosesAllTags() throws IOException {
        assertEquals("<b><u><span style=\"color: blue;\">styled</span></u></b> plain",
                convert("\u001B[1;4;34mstyled\u001B[0m plain"));
    }

    @Test
    public void testMultipleSequences() throws IOException {
        assertEquals("<span style=\"color: red;\">red</span><span style=\"color: green;\">green</span>",
                convert("\u001B[31mred\u001B[0m\u001B[32mgreen\u001B[0m"));
    }

    @Test
    public void testEmptyEscapeActsAsReset() throws IOException {
        assertEquals("<b>bold</b> normal",
                convert("\u001B[1mbold\u001B[m normal"));
    }

    @Test
    public void testNonSgrEscapeIgnored() throws IOException {
        // CSI H (cursor position) should be ignored
        assertEquals("hello", convert("\u001B[Hhello"));
    }

    @Test
    public void testAllBasicForegroundColors() throws IOException {
        String[] colors = { "black", "red", "green", "yellow", "blue", "magenta", "cyan", "white" };
        for (int i = 0; i < colors.length; i++) {
            int code = 30 + i;
            assertEquals("<span style=\"color: " + colors[i] + ";\">x</span>",
                    convert("\u001B[" + code + "mx\u001B[0m"));
        }
    }

    @Test
    public void testAllBasicBackgroundColors() throws IOException {
        String[] colors = { "black", "red", "green", "yellow", "blue", "magenta", "cyan", "white" };
        for (int i = 0; i < colors.length; i++) {
            int code = 40 + i;
            assertEquals("<span style=\"background-color: " + colors[i] + ";\">x</span>",
                    convert("\u001B[" + code + "mx\u001B[0m"));
        }
    }
}
