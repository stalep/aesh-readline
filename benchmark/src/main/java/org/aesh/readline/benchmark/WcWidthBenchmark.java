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

import org.aesh.terminal.utils.WcWidth;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for WcWidth character width calculation.
 * <p>
 * WcWidth.width() is called per-character for cursor positioning in
 * Buffer operations, completion display, and prompt width calculation.
 * It uses binary search over Unicode ranges.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Thread)
public class WcWidthBenchmark {

    // ASCII (most common case — Latin text)
    private static final int ASCII_A = 'a';
    private static final int ASCII_SPACE = ' ';
    private static final int ASCII_TILDE = '~';

    // Control characters (width = -1 or 0)
    private static final int NUL = 0;
    private static final int TAB = 9;
    private static final int ESC = 27;

    // CJK characters (width = 2)
    private static final int CJK_UNIFIED = 0x4E2D;   // 中
    private static final int CJK_KATAKANA = 0x30A2;   // ア
    private static final int CJK_HANGUL = 0xAC00;     // 가

    // Emoji (width = 2)
    private static final int EMOJI_SMILE = 0x1F600;   // 😀
    private static final int EMOJI_HEART = 0x2764;     // ❤

    // Zero-width characters
    private static final int COMBINING_ACUTE = 0x0301; // combining accent
    private static final int ZWJ = 0x200D;             // zero-width joiner
    private static final int SOFT_HYPHEN = 0x00AD;

    // ========== Single character lookups ==========

    @Benchmark
    public void asciiChar(Blackhole bh) {
        bh.consume(WcWidth.width(ASCII_A));
    }

    @Benchmark
    public void asciiSpace(Blackhole bh) {
        bh.consume(WcWidth.width(ASCII_SPACE));
    }

    @Benchmark
    public void controlChar(Blackhole bh) {
        bh.consume(WcWidth.width(TAB));
    }

    @Benchmark
    public void cjkChar(Blackhole bh) {
        bh.consume(WcWidth.width(CJK_UNIFIED));
    }

    @Benchmark
    public void emoji(Blackhole bh) {
        bh.consume(WcWidth.width(EMOJI_SMILE));
    }

    @Benchmark
    public void combiningChar(Blackhole bh) {
        bh.consume(WcWidth.width(COMBINING_ACUTE));
    }

    // ========== String width calculation (realistic) ==========

    private static final String ASCII_LINE = "The quick brown fox jumps over the lazy dog";
    private static final String CJK_LINE = "\u4E2D\u6587\u6D4B\u8BD5\u5B57\u7B26\u4E32";
    private static final String MIXED_LINE = "Hello \u4E16\u754C World \u30C6\u30B9\u30C8";
    private static final String COMPLETION_CANDIDATE = "--output-format=json";

    @Benchmark
    public void stringWidthAscii(Blackhole bh) {
        int width = 0;
        for (int i = 0; i < ASCII_LINE.length(); i++) {
            width += WcWidth.width(ASCII_LINE.codePointAt(i));
        }
        bh.consume(width);
    }

    @Benchmark
    public void stringWidthCjk(Blackhole bh) {
        int width = 0;
        for (int i = 0; i < CJK_LINE.length(); i++) {
            int cp = CJK_LINE.codePointAt(i);
            width += WcWidth.width(cp);
            if (Character.isSupplementaryCodePoint(cp)) i++;
        }
        bh.consume(width);
    }

    @Benchmark
    public void stringWidthMixed(Blackhole bh) {
        int width = 0;
        for (int i = 0; i < MIXED_LINE.length(); i++) {
            int cp = MIXED_LINE.codePointAt(i);
            width += WcWidth.width(cp);
            if (Character.isSupplementaryCodePoint(cp)) i++;
        }
        bh.consume(width);
    }

    @Benchmark
    public void completionCandidateWidth(Blackhole bh) {
        int width = 0;
        for (int i = 0; i < COMPLETION_CANDIDATE.length(); i++) {
            width += WcWidth.width(COMPLETION_CANDIDATE.codePointAt(i));
        }
        bh.consume(width);
    }

    // ========== Throughput ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void asciiThroughput(Blackhole bh) {
        bh.consume(WcWidth.width(ASCII_A));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void cjkThroughput(Blackhole bh) {
        bh.consume(WcWidth.width(CJK_UNIFIED));
    }
}
