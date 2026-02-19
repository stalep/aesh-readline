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

import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.IntArrayBuilder;
import org.aesh.terminal.utils.Parser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Micro-benchmarks for IntArrayBuilder, the dynamic int[] builder used in all
 * ANSI sequence construction.
 * <p>
 * IntArrayBuilder starts at capacity 1 and grows by {@code 2*len + 2}, so a
 * typical 100-int output requires ~10 resize+copy operations. These benchmarks
 * quantify the cost of this growth strategy and compare it with pre-sized builders.
 *
 * @author Aesh Team
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class IntArrayBuilderBenchmark {

    // Test data: 5-int array for batch appends
    private static final int[] FIVE_INTS = {65, 66, 67, 68, 69};
    // Test data: 100-int array for single-shot append
    private static final int[] HUNDRED_INTS = generateInts(100);
    // ANSI color sequences as int[]
    private static final int[] ANSI_RED = Parser.toCodePoints(ANSI.RED_TEXT);
    private static final int[] ANSI_GREEN = Parser.toCodePoints(ANSI.GREEN_TEXT);
    private static final int[] ANSI_RESET = Parser.toCodePoints(ANSI.RESET);
    // Simulated prompt ANSI output
    private static final int[] PROMPT_ANSI = Parser.toCodePoints("\u001B[0;32m> \u001B[0m");
    // Simulated 80-column line content
    private static final int[] LINE_CONTENT = generateInts(80);
    // Cursor sync sequence (typical: ESC[4D)
    private static final int[] CURSOR_SYNC = {27, '[', '4', 'D'};

    // ========== Growth from Empty (worst case) ==========

    @Benchmark
    public void appendSingleIntsFromEmpty(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append(65 + (i % 26));
        }
        bh.consume(builder.toArray());
    }

    @Benchmark
    public void appendSmallArraysFromEmpty(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder();
        for (int i = 0; i < 20; i++) {
            builder.append(FIVE_INTS);
        }
        bh.consume(builder.toArray());
    }

    @Benchmark
    public void appendOneArrayFromEmpty(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder();
        builder.append(HUNDRED_INTS);
        bh.consume(builder.toArray());
    }

    // ========== Pre-sized Comparison ==========

    @Benchmark
    public void appendSingleIntsPreSized(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder(100);
        for (int i = 0; i < 100; i++) {
            builder.append(65 + (i % 26));
        }
        bh.consume(builder.toArray());
    }

    @Benchmark
    public void appendSmallArraysPreSized(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder(100);
        for (int i = 0; i < 20; i++) {
            builder.append(FIVE_INTS);
        }
        bh.consume(builder.toArray());
    }

    @Benchmark
    public void appendOneArrayPreSized(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder(100);
        builder.append(HUNDRED_INTS);
        bh.consume(builder.toArray());
    }

    // ========== Realistic ANSI Building Patterns ==========

    @Benchmark
    public void simulatePromptOutput(Blackhole bh) {
        // Build what printInsertedData builds: prompt ANSI + content + cursor sync
        IntArrayBuilder builder = new IntArrayBuilder();
        builder.append(PROMPT_ANSI);
        builder.append("hello world".codePoints().toArray());
        builder.append(CURSOR_SYNC);
        bh.consume(builder.toArray());
    }

    @Benchmark
    public void simulatePromptOutputPreSized(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder(200);
        builder.append(PROMPT_ANSI);
        builder.append("hello world".codePoints().toArray());
        builder.append(CURSOR_SYNC);
        bh.consume(builder.toArray());
    }

    @Benchmark
    public void simulateFullLinePrint(Blackhole bh) {
        // Full 80-column line output from default builder
        IntArrayBuilder builder = new IntArrayBuilder();
        builder.append(ANSI_RED);
        builder.append(LINE_CONTENT);
        builder.append(ANSI_RESET);
        builder.append(CURSOR_SYNC);
        bh.consume(builder.toArray());
    }

    @Benchmark
    public void simulateFullLinePrintPreSized(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder(128);
        builder.append(ANSI_RED);
        builder.append(LINE_CONTENT);
        builder.append(ANSI_RESET);
        builder.append(CURSOR_SYNC);
        bh.consume(builder.toArray());
    }

    // ========== toArray() Overhead ==========

    @Benchmark
    public void toArraySmall(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder(16);
        for (int i = 0; i < 10; i++) {
            builder.append(65 + i);
        }
        bh.consume(builder.toArray());
    }

    @Benchmark
    public void toArrayMedium(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder(128);
        builder.append(HUNDRED_INTS);
        bh.consume(builder.toArray());
    }

    @Benchmark
    public void toArrayLarge(Blackhole bh) {
        IntArrayBuilder builder = new IntArrayBuilder(1024);
        for (int i = 0; i < 10; i++) {
            builder.append(HUNDRED_INTS);
        }
        bh.consume(builder.toArray());
    }

    // ========== Parameterized Growth Comparison ==========

    @State(Scope.Thread)
    public static class GrowthState {
        @Param({"10", "100", "500", "1000"})
        public int targetSize;
    }

    @Benchmark
    public void growFromEmptyParameterized(Blackhole bh, GrowthState state) {
        IntArrayBuilder builder = new IntArrayBuilder();
        for (int i = 0; i < state.targetSize; i++) {
            builder.append(65 + (i % 26));
        }
        bh.consume(builder.toArray());
    }

    @Benchmark
    public void growPreSizedParameterized(Blackhole bh, GrowthState state) {
        IntArrayBuilder builder = new IntArrayBuilder(state.targetSize);
        for (int i = 0; i < state.targetSize; i++) {
            builder.append(65 + (i % 26));
        }
        bh.consume(builder.toArray());
    }

    // ========== Helpers ==========

    private static int[] generateInts(int count) {
        int[] arr = new int[count];
        for (int i = 0; i < count; i++) {
            arr[i] = 65 + (i % 26);
        }
        return arr;
    }
}
