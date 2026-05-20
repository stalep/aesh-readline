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
package org.aesh.readline.benchmark;

import java.util.concurrent.TimeUnit;

import org.aesh.terminal.parser.VtHandler;
import org.aesh.terminal.parser.VtParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for VtParser escape sequence classification performance.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Thread)
public class VtParserBenchmark {

    // Printable text — the hot path (95%+ of input)
    private static final byte[] PRINTABLE_SHORT = "hello".getBytes();
    private static final byte[] PRINTABLE_LINE = "The quick brown fox jumps over the lazy dog".getBytes();

    // CSI sequences
    private static final byte[] CSI_ARROW_UP = "\033[A".getBytes();
    private static final byte[] CSI_CURSOR_POS = "\033[10;20H".getBytes();
    private static final byte[] CSI_SGR_COLOR = "\033[38;2;255;128;0m".getBytes();
    private static final byte[] CSI_PRIVATE = "\033[?1049h".getBytes();
    private static final byte[] CSI_DA1_RESPONSE = "\033[?65;1;9c".getBytes();

    // SGR mouse event
    private static final byte[] MOUSE_SGR = "\033[<0;42;17M".getBytes();

    // OSC sequences
    private static final byte[] OSC_TITLE = "\033]0;My Terminal Title\007".getBytes();
    private static final byte[] OSC_COLOR = "\033]10;rgb:ffff/8080/0000\007".getBytes();

    // Mixed input — realistic terminal session
    private static final byte[] MIXED_INPUT;
    static {
        StringBuilder sb = new StringBuilder();
        sb.append("ls -la /tmp");           // typing
        sb.append("\033[A");                // arrow up
        sb.append("\033[A");                // arrow up
        sb.append("\033[B");                // arrow down
        sb.append("grep foo bar.txt");      // typing
        sb.append("\r");                    // enter
        sb.append("\033[?1049h");           // alt screen on
        sb.append("\033[2J");               // clear screen
        sb.append("\033[1;1H");             // cursor home
        sb.append("Hello, World!");         // typing
        sb.append("\033[?1049l");           // alt screen off
        MIXED_INPUT = sb.toString().getBytes();
    }

    private VtParser parser;
    private CountingHandler handler;

    @Setup(Level.Trial)
    public void setup() {
        handler = new CountingHandler();
        parser = new VtParser(handler);
    }

    @Setup(Level.Invocation)
    public void resetParser() {
        parser.reset();
        handler.reset();
    }

    // ========== Printable text (hot path) ==========

    @Benchmark
    public void printableShort(Blackhole bh) {
        parser.advance(PRINTABLE_SHORT, 0, PRINTABLE_SHORT.length);
        bh.consume(handler.printCount);
    }

    @Benchmark
    public void printableLine(Blackhole bh) {
        parser.advance(PRINTABLE_LINE, 0, PRINTABLE_LINE.length);
        bh.consume(handler.printCount);
    }

    // ========== CSI sequences ==========

    @Benchmark
    public void csiArrowKey(Blackhole bh) {
        parser.advance(CSI_ARROW_UP, 0, CSI_ARROW_UP.length);
        bh.consume(handler.csiCount);
    }

    @Benchmark
    public void csiCursorPosition(Blackhole bh) {
        parser.advance(CSI_CURSOR_POS, 0, CSI_CURSOR_POS.length);
        bh.consume(handler.csiCount);
    }

    @Benchmark
    public void csiSgrColor(Blackhole bh) {
        parser.advance(CSI_SGR_COLOR, 0, CSI_SGR_COLOR.length);
        bh.consume(handler.csiCount);
    }

    @Benchmark
    public void csiPrivateMode(Blackhole bh) {
        parser.advance(CSI_PRIVATE, 0, CSI_PRIVATE.length);
        bh.consume(handler.csiCount);
    }

    @Benchmark
    public void csiDa1Response(Blackhole bh) {
        parser.advance(CSI_DA1_RESPONSE, 0, CSI_DA1_RESPONSE.length);
        bh.consume(handler.csiCount);
    }

    @Benchmark
    public void mouseSgr(Blackhole bh) {
        parser.advance(MOUSE_SGR, 0, MOUSE_SGR.length);
        bh.consume(handler.csiCount);
    }

    // ========== OSC sequences ==========

    @Benchmark
    public void oscTitle(Blackhole bh) {
        parser.advance(OSC_TITLE, 0, OSC_TITLE.length);
        bh.consume(handler.oscCount);
    }

    @Benchmark
    public void oscColorQuery(Blackhole bh) {
        parser.advance(OSC_COLOR, 0, OSC_COLOR.length);
        bh.consume(handler.oscCount);
    }

    // ========== Mixed input ==========

    @Benchmark
    public void mixedInput(Blackhole bh) {
        parser.advance(MIXED_INPUT, 0, MIXED_INPUT.length);
        bh.consume(handler.printCount + handler.csiCount + handler.oscCount);
    }

    // ========== Throughput ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void printableThroughput(Blackhole bh) {
        parser.advance(PRINTABLE_LINE, 0, PRINTABLE_LINE.length);
        bh.consume(handler.printCount);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void mixedThroughput(Blackhole bh) {
        parser.advance(MIXED_INPUT, 0, MIXED_INPUT.length);
        bh.consume(handler.printCount + handler.csiCount);
    }

    // ========== Code point path ==========

    private static final int[] CODEPOINT_ARROW = { 27, 91, 65 };
    private static final int[] CODEPOINT_TEXT = "Hello".codePoints().toArray();

    @Benchmark
    public void codePointArrowKey(Blackhole bh) {
        parser.advance(CODEPOINT_ARROW, 0, CODEPOINT_ARROW.length);
        bh.consume(handler.csiCount);
    }

    @Benchmark
    public void codePointText(Blackhole bh) {
        parser.advance(CODEPOINT_TEXT, 0, CODEPOINT_TEXT.length);
        bh.consume(handler.printCount);
    }

    /**
     * Minimal handler that counts events without allocating.
     */
    static class CountingHandler implements VtHandler {
        int printCount;
        int csiCount;
        int escCount;
        int oscCount;
        int executeCount;

        void reset() {
            printCount = 0;
            csiCount = 0;
            escCount = 0;
            oscCount = 0;
            executeCount = 0;
        }

        @Override
        public void print(int codePoint) { printCount++; }

        @Override
        public void execute(int controlChar) { executeCount++; }

        @Override
        public void escDispatch(int finalChar, int[] intermediates, int intermediateCount) {
            escCount++;
        }

        @Override
        public void csiDispatch(int finalChar, int[] params, int paramCount,
                                int[] intermediates, int intermediateCount,
                                boolean hasSubParams) {
            csiCount++;
        }

        @Override
        public void oscEnd(String data) { oscCount++; }
    }
}
