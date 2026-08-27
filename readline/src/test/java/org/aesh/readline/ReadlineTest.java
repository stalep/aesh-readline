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
package org.aesh.readline;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.aesh.readline.completion.Completion;
import org.aesh.readline.cursor.Line;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.prompt.Prompt;
import org.aesh.readline.tty.terminal.TestConnection;
import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Key;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.Config;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class ReadlineTest {

    @Test
    public void testArrowKeys() {
        TestConnection term = new TestReadlineConnection();
        term.read("1234");
        term.read(Key.LEFT);
        term.read(Key.LEFT);
        term.read(Key.BACKSPACE);
        term.read(Key.ENTER);
        term.assertLine("134");
    }

    @Test
    public void testPaste() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("1234\nfoo bar\ngah bah");
        term.assertLine("1234");
        term.readline();
        term.assertLine("foo bar");
        term.readline();
        term.assertBuffer("gah bah");
    }

    @Test
    public void testMasking() {
        Prompt prompt = new Prompt(": ", '#');
        TestReadlineConnection term = new TestReadlineConnection(null, null, null, prompt);
        term.setSignalHandler(null);
        term.read("foo bar");
        assertEquals(": #######", term.getOutputBuffer());
        term.read(Key.BACKSPACE);
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_D);
        term.read(Key.ENTER);
        term.assertLine("oo ba");

        prompt = new Prompt("", '\0');
        term.setPrompt(prompt);
        term.readline();
        term.read("foo bar");
        assertEquals("", term.getOutputBuffer());
        term.read(Key.BACKSPACE);
        term.read(Key.BACKSPACE);
        term.read(Key.ENTER);
        term.assertLine("foo b");
    }

    @Test
    public void testEmptyPrompt() {
        TestReadlineConnection term = new TestReadlineConnection(new Prompt(""));
        term.read("foo");
        term.sizeHandler().accept(new Size(80, 80));

        assertEquals("foofoo", term.getOutputBuffer());
    }

    @Test
    public void testMultiLine() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("foo \\");
        term.clearOutputBuffer();
        term.read(Key.ENTER);
        term.assertLine(null);
        // Multi-line mode: continuation prompt should appear in output
        String output = term.getOutputBuffer();
        Assert.assertTrue("Output should contain continuation prompt, got: " + output,
                output.contains("> "));
        term.read("bar\n");
        // Backslash-continuation: submitted content has lines joined (no \n)
        term.assertLine("foo bar");
    }

    @Test
    public void testMultiLineQuote() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("\"foo ");
        term.clearOutputBuffer();
        term.read(Key.ENTER);
        term.assertLine(null);
        // Multi-line mode: continuation prompt should appear in output
        String output = term.getOutputBuffer();
        Assert.assertTrue("Output should contain continuation prompt, got: " + output,
                output.contains("> "));
        term.read("bar\"\n");
        // Open-quote continuation: newline preserved in submitted content
        term.assertLine("\"foo " + Config.getLineSeparator() + "bar\"");
    }

    @Test
    public void testMultiLineDelete() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("foo \\");
        term.clearOutputBuffer();
        term.read(Key.ENTER);
        // In unified buffer, the buffer contains "foo \n" — asString() strips
        // backslash newlines, so assertBuffer sees "foo "
        term.assertBuffer("foo ");
        term.assertLine(null);
        term.read("bar");
        term.read(Key.BACKSPACE);
        term.read(Key.BACKSPACE);
        term.read(Key.BACKSPACE);
        term.read(Key.BACKSPACE);
        term.read(Key.ENTER);
        term.assertLine("foo ");
    }

    @Test
    public void testSingleCompleteResult() {
        List<Completion> completions = new ArrayList<>();
        completions.add(completeOperation -> {
            if (completeOperation.getBuffer().equals("f"))
                completeOperation.addCompletionCandidate("foo");
            else if (completeOperation.getBuffer().equals("b"))
                completeOperation.addCompletionCandidate("bar");
        });

        TestReadlineConnection term = new TestReadlineConnection(completions);

        term.read("fo");
        term.read(Key.CTRL_I);
        term.assertBuffer("fo");
        term.read(Key.BACKSPACE);
        term.read(Key.CTRL_I);
        term.assertBuffer("foo ");
        term.read("1");
        term.assertBuffer("foo 1");
        term.read(Key.ENTER);
        term.assertLine("foo 1");
    }

    @Test
    public void testMultipleCompleteResults() {
        List<Completion> completions = new ArrayList<>();
        completions.add(completeOperation -> {
            if (completeOperation.getBuffer().equals("f")) {
                completeOperation.addCompletionCandidate("foo");
                completeOperation.addCompletionCandidate("foo bar");
            } else if (completeOperation.getBuffer().equals("foo")) {
                completeOperation.addCompletionCandidate("foo");
                completeOperation.addCompletionCandidate("foo bar");
            } else if (completeOperation.getBuffer().equals("b")) {
                completeOperation.addCompletionCandidate("bar bar");
                completeOperation.addCompletionCandidate("bar baar");
            }
        });

        TestReadlineConnection term = new TestReadlineConnection(completions);

        term.read("f");
        term.read(Key.CTRL_I);
        term.assertBuffer("foo");
        term.clearOutputBuffer();
        term.read(Key.CTRL_I);
        assertEquals(Config.getLineSeparator() + "foo  foo bar  " +
                Config.getLineSeparator() + term.getPrompt() + "foo", term.getOutputBuffer());
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("b");
        term.read(Key.CTRL_I);
        term.assertBuffer("bar\\ ba");
    }

    @Test
    public void testCompleteResultsMultipleLines() {
        List<Completion> completions = new ArrayList<>();
        completions.add(completeOperation -> {
            if (completeOperation.getBuffer().equals("ff")) {
                completeOperation.addCompletionCandidate("ffoo");
            } else if (completeOperation.getBuffer().endsWith("f")) {
                completeOperation.addCompletionCandidate(completeOperation.getBuffer() + "oo");
            } else if (completeOperation.getBuffer().endsWith("foo")) {
                completeOperation.addCompletionCandidate(completeOperation.getBuffer() + "foo");
                completeOperation.addCompletionCandidate(completeOperation.getBuffer() + "foo bar");
            } else if (completeOperation.getBuffer().endsWith("b")) {
                completeOperation.addCompletionCandidate(completeOperation.getBuffer() + "bar bar");
                completeOperation.addCompletionCandidate(completeOperation.getBuffer() + "bar baar");
            }
        });

        Size termSize = new Size(10, 10);
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder().build(), completions, termSize);

        term.read("ff");
        term.read(Key.CTRL_I);
        term.assertBuffer("ffoo ");
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("11111111111f");
        term.read(Key.CTRL_I);
        term.assertBuffer("11111111111foo ");
    }

    @Test
    public void testCompletionDoNotMatchBuffer() {
        List<Completion> completions = new ArrayList<>();
        completions.add(completeOperation -> {
            if (completeOperation.getBuffer().endsWith("f")) {
                completeOperation.addCompletionCandidate("foo");
                completeOperation.setOffset(2);
            } else if (completeOperation.getBuffer().endsWith("foo")) {
                completeOperation.addCompletionCandidate("foo bar");
                completeOperation.setOffset(completeOperation.getCursor() - 3);
            } else if (completeOperation.getBuffer().endsWith("b")) {
                completeOperation.addCompletionCandidate("bar bar");
                completeOperation.addCompletionCandidate("bar baar");
                completeOperation.setOffset(completeOperation.getCursor() - 1);
            }
        });

        TestReadlineConnection term = new TestReadlineConnection(completions);

        term.read("oof");
        term.read(Key.CTRL_I);
        term.assertBuffer("oofoo ");
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("bab");
        term.read(Key.CTRL_I);
        term.assertBuffer("babar\\ ba");
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("foo foo");
        term.read(Key.CTRL_I);
        term.assertBuffer("foo foo bar ");
    }

    @Test
    public void testCompletionOnMultiline() {
        List<Completion> completions = new ArrayList<>();
        completions.add(completeOperation -> {
            if (completeOperation.getBuffer().endsWith("f")) {
                completeOperation.addCompletionCandidate("foo");
                completeOperation.setOffset(completeOperation.getCursor() - 1);
            } else if (completeOperation.getBuffer().endsWith("foo")) {
                completeOperation.addCompletionCandidate("foo bar");
                completeOperation.setOffset(completeOperation.getCursor() - 3);
            } else if (completeOperation.getBuffer().endsWith("b")) {
                completeOperation.addCompletionCandidate("bar bar");
                completeOperation.addCompletionCandidate("bar baar");
                completeOperation.setOffset(completeOperation.getCursor() - 1);
            }
        });

        TestReadlineConnection term = new TestReadlineConnection(completions);

        term.read("fooish \\\noof");
        term.read(Key.CTRL_I);
        term.assertBuffer("fooish oofoo ");
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("bar bar \\\n bab");
        term.read(Key.CTRL_I);
        term.assertBuffer("bar bar  babar\\ ba");
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("foo \\\n foo \\\nfoo");
        term.read(Key.CTRL_I);
        term.assertBuffer("foo  foo foo bar ");

    }

    @Test
    public void testLineContentsAfterCursorMovement() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("12345");
        int termWidth = term.size().getWidth();
        Buffer buffer = new Buffer();
        buffer.insert((c) -> {
        }, term.getOutputBuffer(), term.getOutputBuffer().length());
        buffer.move((c) -> {
        }, -3, termWidth);
        Line line = new Line(buffer, term, termWidth);

        String s = line.getLineFromCursor();
        assertEquals("345", s);

        s = line.getLineToCursor();
        assertEquals(": 12", s);
    }

    @Test
    public void testMultiLineDisableForSingleQuote() {
        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.NO_MULTI_LINE_ON_QUOTE, 2);
        TestReadlineConnection term = new TestReadlineConnection(flags);
        term.read("'foo ");
        term.clearOutputBuffer();
        term.read(Key.ENTER);
        term.assertLine("'foo ");
    }

    @Test
    public void testMultiLineDisableForDoubleQuote() {
        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.NO_MULTI_LINE_ON_QUOTE, 1);
        TestReadlineConnection term = new TestReadlineConnection(flags);
        term.read("\"foo ");
        term.clearOutputBuffer();
        term.read(Key.ENTER);
        term.assertLine("\"foo ");
    }

    @Test
    public void testNoDiscardOfComment() {
        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestReadlineConnection term;
        term = new TestReadlineConnection(); // default behavior, discard comment
        term.read("# this is a comment");
        term.clearOutputBuffer();
        term.read(Key.ENTER);
        term.assertLine(null);
        flags.put(ReadlineFlag.NO_COMMENT_DISCARD, 1);
        term = new TestReadlineConnection(flags); // do not discard comment
        term.read("# this is not a comment");
        term.clearOutputBuffer();
        term.read(Key.ENTER);
        term.assertLine("# this is not a comment");
    }

    /**
     * Verify that input sent between readline cycles (in the requestHandler
     * callback) is not lost. Regression test for #233.
     */
    @Test
    public void testInputBetweenReadlineCycles() {
        TestReadline readline = new TestReadline();
        TestReadlineConnection conn = new TestReadlineConnection(readline,
                EditModeBuilder.builder().build(),
                null, null, null, null,
                new EnumMap<>(ReadlineFlag.class));

        List<String> results = new ArrayList<>();

        // First readline cycle
        readline.readline(conn, new Prompt(": "), line -> {
            results.add(line);
            // Start second readline cycle from inside the requestHandler.
            // This runs after finish() restores the handler.
            readline.readline(conn, new Prompt(": "), line2 -> {
                results.add(line2);
            });
            // Send input for the second cycle — arrives after finish()
            // set the buffering handler, before start() sets the new one.
            conn.read("second");
            conn.read(Key.ENTER);
        });

        // Send input for the first cycle
        conn.read("first");
        conn.read(Key.ENTER);

        assertEquals(2, results.size());
        assertEquals("first", results.get(0));
        assertEquals("second", results.get(1));
    }

    // ---- Multi-line editing tests (unified buffer, #257) ----

    @Test
    public void testMultiLineHistoryStoresSingleLine() {
        TestReadlineConnection term = new TestReadlineConnection();
        // Submit a multi-line backslash continuation
        term.read("echo first \\");
        term.read(Key.ENTER);
        term.assertLine(null); // continuation
        term.read("second\n"); // submit
        // Should submit joined content (backslash newline stripped)
        term.assertLine("echo first second");

        // Start a new readline cycle
        term.readline();

        // Press Up to navigate history — should show single joined line
        term.read(Key.UP);
        term.assertBuffer("echo first second");
    }

    @Test
    public void testMultiLineUpDown() {
        TestReadlineConnection term = new TestReadlineConnection();
        // Type first line with backslash continuation
        term.read("first\\");
        term.read(Key.ENTER);
        term.assertLine(null); // not submitted yet
        // Type second line
        term.read("second");
        // Cursor is on line 1 ("second"). Press Up to go to line 0
        term.read(Key.UP);
        // Now on line 0. Type Enter to submit (since "first" doesn't end with \)
        // But wait — the buffer is "first\nsecond" and we're on line 0.
        // Pressing Enter checks the FULL buffer for completion.
        // The buffer no longer ends with \ and has no open quotes, so it submits.
        term.read(Key.ENTER);
        // Submitted content: backslash newlines stripped → "firstsecond"
        term.assertLine("firstsecond");
    }

    @Test
    public void testMultiLineUpDownColumnClamping() {
        TestReadlineConnection term = new TestReadlineConnection();
        // Line 0: "abcdef\" (7 chars, backslash continuation)
        term.read("abcdef\\");
        term.read(Key.ENTER);
        term.assertLine(null);
        // Line 1: "xy" (2 chars) — shorter than line 0
        term.read("xy");
        // Cursor is at column 2 on line 1. Press Up.
        // Target column is min(2, 6) = 2 → position 2 on line 0 ('c')
        term.read(Key.UP);
        // Now press Down — back to line 1 at column min(2, 2) = 2
        term.read(Key.DOWN);
        // Submit
        term.read(Key.ENTER);
        term.assertLine("abcdefxy");
    }

    @Test
    public void testMultiLineUpFallsToHistoryOnFirstLine() {
        TestReadlineConnection term = new TestReadlineConnection();
        // First, submit a history entry
        term.read("history-entry\n");
        term.assertLine("history-entry");
        term.readline();
        // Now type a single-line command and press Up
        term.read("current");
        term.read(Key.UP);
        // Should navigate history, replacing buffer with "history-entry"
        term.assertBuffer("history-entry");
    }

    // ---- Phase 2: Line-boundary-aware actions ----

    @Test
    public void testHomeEndOnMultiLine() {
        TestReadlineConnection term = new TestReadlineConnection();
        // Create two-line buffer: "first\nsecond"
        term.read("first\\");
        term.read(Key.ENTER);
        term.assertLine(null);
        term.read("second");

        // Cursor is at end of "second" (line 1). Press Home.
        term.read(Key.HOME);
        // Should move to start of line 1 ("second"), not start of entire buffer
        // Verify by pressing End then Enter — should get full content
        term.read(Key.END);
        term.read(Key.ENTER);
        term.assertLine("firstsecond");
    }

    @Test
    public void testHomeOnLine0() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("first\\");
        term.read(Key.ENTER);
        term.assertLine(null);
        term.read("second");

        // Move to line 0
        term.read(Key.UP);
        // Press Home — should stay on line 0, move to column 0
        term.read(Key.HOME);
        // Type 'X' at position 0 of line 0
        term.read("X");
        term.read(Key.ENTER);
        term.assertLine("Xfirstsecond");
    }

    @Test
    public void testCtrlKOnMultiLine() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("first\\");
        term.read(Key.ENTER);
        term.assertLine(null);
        term.read("second");

        // Move to line 0, then to column 3
        term.read(Key.UP);
        term.read(Key.HOME);
        term.read(Key.RIGHT);
        term.read(Key.RIGHT);
        term.read(Key.RIGHT);

        // Ctrl+K should kill from cursor to end of line 0 only ("st")
        term.read(Key.CTRL_K);

        // Buffer should now be "fir\nsecond" (line 0 = "fir", line 1 = "second")
        term.read(Key.ENTER);
        term.assertLine("firsecond");
    }

    @Test
    public void testCtrlUOnMultiLine() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("first\\");
        term.read(Key.ENTER);
        term.assertLine(null);
        term.read("second");

        // Move to line 0, then to column 3
        term.read(Key.UP);
        term.read(Key.HOME);
        term.read(Key.RIGHT);
        term.read(Key.RIGHT);
        term.read(Key.RIGHT);

        // Ctrl+U should kill from cursor back to start of line 0 only ("fir")
        term.read(Key.CTRL_U);

        // Buffer should now be "st\nsecond" (line 0 = "st", line 1 = "second")
        term.read(Key.ENTER);
        term.assertLine("stsecond");
    }

}
