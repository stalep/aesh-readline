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

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.aesh.readline.prompt.Prompt;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks simulating TUI framework frame rendering through aesh-readline.
 * <p>
 * Models the tamboui aesh backend pattern: build frame output into a
 * StringBuilder, then call Connection.write(String) once per frame.
 * Measures achievable frame rates at various terminal sizes.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Thread)
public class TuiFrameRateBenchmark {

    private static final String CSI = "\033[";
    private static final String RESET = "\033[0m";

    @Param({"80x24", "120x40", "200x60"})
    private String terminalSize;

    private int cols;
    private int rows;
    private FrameConnection connection;

    // Pre-built frame content for different scenarios
    private String plainFrame;
    private String coloredFrame;
    private String heavyStyleFrame;
    private String partialUpdate10;
    private String partialUpdate3;

    @Setup(Level.Trial)
    public void setup() {
        String[] parts = terminalSize.split("x");
        cols = Integer.parseInt(parts[0]);
        rows = Integer.parseInt(parts[1]);

        connection = new FrameConnection(cols, rows);

        plainFrame = buildPlainFrame();
        coloredFrame = buildColoredFrame();
        heavyStyleFrame = buildHeavyStyleFrame();
        partialUpdate10 = buildPartialUpdate(10);
        partialUpdate3 = buildPartialUpdate(3);
    }

    @Setup(Level.Invocation)
    public void resetStream() {
        connection.resetStream();
    }

    // ========== Full frame redraws ==========

    /**
     * Plain text frame — no colors, just cursor positioning + text.
     * The minimum cost of a full redraw.
     */
    @Benchmark
    public void fullFramePlain(Blackhole bh) {
        connection.write(plainFrame);
        bh.consume(connection.bytesWritten());
    }

    /**
     * Colored frame — each line has foreground + background color changes.
     * Typical for a TUI with colored status bars and content.
     */
    @Benchmark
    public void fullFrameColored(Blackhole bh) {
        connection.write(coloredFrame);
        bh.consume(connection.bytesWritten());
    }

    /**
     * Heavy style frame — every cell has different style (bold, italic,
     * underline, color changes). Worst case for style-diff output.
     */
    @Benchmark
    public void fullFrameHeavyStyle(Blackhole bh) {
        connection.write(heavyStyleFrame);
        bh.consume(connection.bytesWritten());
    }

    // ========== Partial updates (dirty regions) ==========

    /**
     * Partial update — only 10 lines changed (e.g., scrolling content area).
     */
    @Benchmark
    public void partialUpdate10Lines(Blackhole bh) {
        connection.write(partialUpdate10);
        bh.consume(connection.bytesWritten());
    }

    /**
     * Partial update — only 3 lines changed (e.g., status bar + cursor line).
     */
    @Benchmark
    public void partialUpdate3Lines(Blackhole bh) {
        connection.write(partialUpdate3);
        bh.consume(connection.bytesWritten());
    }

    // ========== StringBuilder accumulation + write ==========

    /**
     * Full frame built in StringBuilder then written — the exact tamboui pattern.
     * Measures StringBuilder + Connection.write(String) together.
     */
    @Benchmark
    public void buildAndWriteFrame(Blackhole bh) {
        StringBuilder sb = new StringBuilder(cols * rows * 2);
        for (int row = 0; row < rows; row++) {
            sb.append(CSI).append(row + 1).append(";1H");
            // Simulated styled content: color + text + reset per line
            sb.append(CSI).append("38;5;").append(row % 256).append('m');
            for (int col = 0; col < cols; col++) {
                sb.append((char) ('A' + ((row + col) % 26)));
            }
            sb.append(RESET);
        }
        connection.write(sb.toString());
        bh.consume(connection.bytesWritten());
    }

    /**
     * Same as buildAndWriteFrame but with synchronized output markers.
     */
    @Benchmark
    public void buildAndWriteFrameSynchronized(Blackhole bh) {
        StringBuilder sb = new StringBuilder(cols * rows * 2);
        sb.append(CSI).append("?2026h"); // begin synchronized output
        for (int row = 0; row < rows; row++) {
            sb.append(CSI).append(row + 1).append(";1H");
            sb.append(CSI).append("38;5;").append(row % 256).append('m');
            for (int col = 0; col < cols; col++) {
                sb.append((char) ('A' + ((row + col) % 26)));
            }
            sb.append(RESET);
        }
        sb.append(CSI).append("?2026l"); // end synchronized output
        connection.write(sb.toString());
        bh.consume(connection.bytesWritten());
    }

    // ========== Throughput (frames per second) ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void framesPerSecondPlain(Blackhole bh) {
        connection.write(plainFrame);
        bh.consume(connection.bytesWritten());
        connection.resetStream();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void framesPerSecondColored(Blackhole bh) {
        connection.write(coloredFrame);
        bh.consume(connection.bytesWritten());
        connection.resetStream();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void framesPerSecondPartial3(Blackhole bh) {
        connection.write(partialUpdate3);
        bh.consume(connection.bytesWritten());
        connection.resetStream();
    }

    // ========== Frame builders ==========

    private String buildPlainFrame() {
        StringBuilder sb = new StringBuilder(cols * rows * 2);
        for (int row = 0; row < rows; row++) {
            sb.append(CSI).append(row + 1).append(";1H");
            for (int col = 0; col < cols; col++) {
                sb.append((char) ('A' + ((row + col) % 26)));
            }
        }
        return sb.toString();
    }

    private String buildColoredFrame() {
        StringBuilder sb = new StringBuilder(cols * rows * 3);
        for (int row = 0; row < rows; row++) {
            sb.append(CSI).append(row + 1).append(";1H");
            // Foreground color per line
            sb.append(CSI).append("38;5;").append(row % 256).append('m');
            // Background color for even rows
            if (row % 2 == 0) {
                sb.append(CSI).append("48;5;").append(236).append('m');
            }
            for (int col = 0; col < cols; col++) {
                sb.append((char) ('A' + ((row + col) % 26)));
            }
            sb.append(RESET);
        }
        return sb.toString();
    }

    private String buildHeavyStyleFrame() {
        StringBuilder sb = new StringBuilder(cols * rows * 8);
        for (int row = 0; row < rows; row++) {
            sb.append(CSI).append(row + 1).append(";1H");
            for (int col = 0; col < cols; col++) {
                // Every cell gets unique styling
                int fg = (row * cols + col) % 256;
                int bg = (fg + 128) % 256;
                sb.append(CSI).append("38;5;").append(fg).append(';');
                sb.append("48;5;").append(bg).append('m');
                if ((col % 3) == 0) sb.append(CSI).append("1m"); // bold
                if ((col % 5) == 0) sb.append(CSI).append("3m"); // italic
                sb.append((char) ('A' + ((row + col) % 26)));
                sb.append(RESET);
            }
        }
        return sb.toString();
    }

    private String buildPartialUpdate(int dirtyLines) {
        StringBuilder sb = new StringBuilder(cols * dirtyLines * 3);
        for (int i = 0; i < dirtyLines && i < rows; i++) {
            int row = (rows / dirtyLines) * i; // spread dirty lines evenly
            sb.append(CSI).append(row + 1).append(";1H");
            sb.append(CSI).append("38;5;").append((row * 7) % 256).append('m');
            for (int col = 0; col < cols; col++) {
                sb.append((char) ('a' + ((row + col) % 26)));
            }
            sb.append(RESET);
        }
        return sb.toString();
    }

    // ========== Mock Connection ==========

    /**
     * Minimal Connection that routes write() through the full
     * String → toCodePoints → Encoder → byte[] pipeline.
     */
    private static class FrameConnection implements Connection {
        private final Device device = new BaseDevice("xterm-256color");
        private final Size size;
        private final ByteArrayOutputStream byteStream = new ByteArrayOutputStream(32768);
        private final Encoder encoder;

        FrameConnection(int cols, int rows) {
            this.size = new Size(cols, rows);
            this.encoder = new Encoder(StandardCharsets.UTF_8, bytes -> byteStream.write(bytes, 0, bytes.length));
        }

        void resetStream() {
            byteStream.reset();
        }

        int bytesWritten() {
            return byteStream.size();
        }

        @Override
        public Connection write(String s) {
            // Uses the same fast path as the real Connection.write(String):
            // Encoder.accept(String) → s.getBytes(UTF_8) → out.accept(byte[])
            encoder.accept(s);
            return this;
        }

        @Override public Device device() { return device; }
        @Override public Size size() { return size; }
        @Override public Consumer<Size> sizeHandler() { return null; }
        @Override public void setSizeHandler(Consumer<Size> h) {}
        @Override public Consumer<Signal> signalHandler() { return null; }
        @Override public void setSignalHandler(Consumer<Signal> h) {}
        @Override public Consumer<int[]> stdinHandler() { return null; }
        @Override public void setStdinHandler(Consumer<int[]> h) {}
        @Override public Consumer<int[]> stdoutHandler() { return encoder::accept; }
        @Override public void setCloseHandler(Consumer<Void> h) {}
        @Override public Consumer<Void> closeHandler() { return null; }
        @Override public void close() {}
        @Override public void openBlocking() {}
        @Override public void openNonBlocking() {}
        @Override public boolean put(Capability c, Object... p) { return false; }
        @Override public Attributes attributes() { return new Attributes(); }
        @Override public void setAttributes(Attributes a) {}
        @Override public Charset inputEncoding() { return StandardCharsets.UTF_8; }
        @Override public Charset outputEncoding() { return StandardCharsets.UTF_8; }
        @Override public boolean supportsAnsi() { return true; }
        @Override public boolean reading() { return false; }
    }
}
