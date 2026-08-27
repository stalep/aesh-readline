/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.readline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.aesh.readline.cursor.CursorLocation;
import org.aesh.readline.cursor.CursorLocator;
import org.aesh.readline.cursor.Line;
import org.aesh.readline.prompt.Prompt;
import org.aesh.terminal.formatting.Color;
import org.aesh.terminal.tty.TerminalConnection;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class CursorLocatorTest {

    private static final String PROMPT = "test> ";
    private static final String MULTI_LINE_PROMPT = "> ";
    private static final int WIDTH = 80;

    @Test
    public void test() {

        { // No cmd
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            check(buffer, 0, 0, PROMPT.length(), WIDTH);
        }

        { // Index is after buffer of size 0.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            CursorLocator locator = buffer.getCursorLocator();
            CursorLocation loc = locator.locate(10, WIDTH);
            assertNull(loc);
        }

        { // Nominal, retrieve index after the prompt inside a cmd.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1";
            int offset = 3;
            buffer.insert((c) -> {
            }, cmd, WIDTH);
            check(buffer, offset, 0, PROMPT.length() + offset, WIDTH);
        }
    }

    @Test
    public void testWrapping() {

        { // Nominal, retrieve index after the prompt inside a cmd longer than
          // terminal width.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1";
            int width = PROMPT.length() + (cmd.length() / 2);
            int offset = (cmd.length() / 2);
            buffer.insert((c) -> {
            }, cmd, width);
            check(buffer, offset, 1, 0, width);
            checkCursor(buffer, 1, offset, width);
        }

        { // Nominal, retrieve index after the prompt inside a cmd longer than
          // terminal width.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1";
            int width = PROMPT.length() + (cmd.length() / 2);
            int offset = (cmd.length() / 2) - 1;
            buffer.insert((c) -> {
            }, cmd, width);
            check(buffer, offset, 0, width - 1, width);
        }

        { // Set a cmd as large as the width.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1";
            int width = PROMPT.length() + cmd.length();
            int offset = cmd.length() - 1;
            buffer.insert((c) -> {
            }, cmd, width);
            check(buffer, offset, 0, offset + PROMPT.length(), width);
            checkCursor(buffer, 1, 0, width);
        }
    }

    @Test
    public void testMultiline() {

        {
            // "cmd --opt1\\" (11 chars) → updateMultiLineBuffer → "cmd --opt1\n" (11 chars, \ replaced by \n)
            // Then insert "--opt2" → "cmd --opt1\n--opt2" (17 chars)
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1\\";
            buffer.insert((c) -> {
            }, cmd, WIDTH);
            buffer.setMultiLine(true);
            buffer.updateMultiLineBuffer();
            String cmd2 = "--opt2";
            buffer.insert((c) -> {
            }, cmd2, WIDTH);
            // In unified buffer, \n is at position 10. Line 1 starts at 11.
            // Cursor is at 17 (end). On line 1, column = (6 + 2) = 8
            int offset = cmd.length() + cmd2.length(); // 11 + 6 = 17 (includes the \n that replaced \)
            check(buffer, offset, 1, cmd2.length() + MULTI_LINE_PROMPT.length(), WIDTH);
            checkCursor(buffer, 1, cmd2.length() + MULTI_LINE_PROMPT.length(), WIDTH);
        }

        {// Check that the cursor location is on col=MULTI_LINE_PROMPT, row=1
         // "cmd --opt1\\" → "cmd --opt1\n" → cursor at 11 (after \n)
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1";
            buffer.insert((c) -> {
            }, cmd + "\\", WIDTH);
            buffer.setMultiLine(true);
            buffer.updateMultiLineBuffer();
            // Check position of last char on line 0: "cmd --opt1" is 10 chars, last is at index 9
            int offset = cmd.length() - 1;
            check(buffer, offset, 0, cmd.length() + PROMPT.length() - 1, WIDTH);
            // Cursor is at 11 (after \n on line 1)
            checkCursor(buffer, 1, MULTI_LINE_PROMPT.length(), WIDTH);
        }

        { //Wrapped and multiline.
          // "cmd --opt1 --opt2 --opt3\\" (25 chars) → "cmd --opt1 --opt2 --opt3\n" (25 chars)
          // Then insert "--opt4" → "cmd --opt1 --opt2 --opt3\n--opt4" (31 chars)
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1 --opt2 --opt3\\";
            int width = PROMPT.length() + (cmd.length() / 2);
            buffer.insert((c) -> {
            }, cmd, width);
            buffer.setMultiLine(true);
            buffer.updateMultiLineBuffer();
            String cmd2 = "--opt4";
            buffer.insert((c) -> {
            }, cmd2, width);
            // \n at position 24. Line 0: "cmd --opt1 --opt2 --opt3" (24 chars + prompt wraps)
            // Line 1: "--opt4" starts at 25
            int offset = cmd.length() + cmd2.length(); // 25 + 6 = 31
            check(buffer, offset, 2, cmd2.length() + MULTI_LINE_PROMPT.length(), width);
        }
    }

    @Test
    public void lineTest() throws IOException {
        TerminalConnection connection = new TerminalConnection();
        Buffer buffer = new Buffer(new Prompt(PROMPT));
        String cmd1 = "cmd --opt1 --opt2 ";
        String cmd2 = "--opt3 --opt4";
        // With unified buffer: getLineToCursor() returns raw content including \n
        String cmdWithNewline = cmd1 + "\n" + cmd2;
        buffer.insert((c) -> {
        }, cmd1 + "\\", WIDTH);
        buffer.setMultiLine(true);
        buffer.updateMultiLineBuffer();
        buffer.insert((c) -> {
        }, cmd2, WIDTH);
        Line line = new Line(buffer, connection, WIDTH);
        String s = line.getLineToCursor();
        Assert.assertEquals(cmdWithNewline, s);
        Assert.assertNotNull(line.getCursorLocator());
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().move(10).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().moveBackward(10).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().moveForward(10).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().moveDown(10).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().moveUp(10).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().colorize(10, Color.DEFAULT, Color.DEFAULT,
                true).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
    }

    private static void checkCursor(Buffer buffer, int row, int col, int width) {
        int c = buffer.multiCursor();
        check(buffer, c, row, col, width);
    }

    private static void check(Buffer buffer, int offset, int row, int col, int width) {
        CursorLocation cursorLoc = buffer.getCursorLocator().locate(offset, width);
        assertEquals("Invalid column " + cursorLoc.getColumn()
                + ". Expected " + col, cursorLoc.getColumn(), col);
        assertEquals("Invalid row " + cursorLoc.getRow() + ". Expected " + row, cursorLoc.getRow(), row);
    }
}
