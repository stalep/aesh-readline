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

import org.aesh.readline.action.KeyMappingTrie;
import org.aesh.terminal.Key;
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
 * Benchmarks for KeyMappingTrie lookup performance.
 * <p>
 * HotSpot's escape analysis eliminates MatchResult heap allocation
 * on the hot path, so the object-returning API is effectively
 * zero-allocation. A packed-long alternative was benchmarked and
 * found to be slower due to HashMap overhead in the pack/unpack path.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Thread)
public class KeyMappingTrieBenchmark {

    // Single-byte inputs
    private static final int CHAR_A = 'a';
    private static final int ESC = 27;

    // Multi-byte sequences
    private static final int[] ARROW_UP = { 27, 91, 65 };
    private static final int[] F12 = { 27, 91, 50, 52, 126 };
    private static final int[] UNKNOWN_CSI = { 27, 91, 60, 48, 59, 53, 59, 51, 77 }; // mouse SGR

    // Buffer with offset (simulates ActionDecoder's offset tracking)
    private static final int[] PADDED_ARROW_UP = { 0, 0, 0, 27, 91, 65, 0, 0 };
    private static final int PADDED_OFFSET = 3;
    private static final int PADDED_LENGTH = 3;

    private KeyMappingTrie trie;

    @Setup(Level.Trial)
    public void setup() {
        trie = new KeyMappingTrie();
        trie.build(Key.values());
    }

    // ========== Single-byte lookups ==========

    @Benchmark
    public void singleByteChar(Blackhole bh) {
        KeyMappingTrie.MatchResult r = trie.matchSingleByte(CHAR_A);
        bh.consume(r.action);
        bh.consume(r.hasPrefix);
    }

    @Benchmark
    public void singleByteEsc(Blackhole bh) {
        KeyMappingTrie.MatchResult r = trie.matchSingleByte(ESC);
        bh.consume(r.action);
        bh.consume(r.hasPrefix);
    }

    // ========== Multi-byte lookups ==========

    @Benchmark
    public void arrowUp(Blackhole bh) {
        KeyMappingTrie.MatchResult r = trie.match(ARROW_UP);
        bh.consume(r.action);
        bh.consume(r.hasPrefix);
    }

    @Benchmark
    public void arrowUpWithOffset(Blackhole bh) {
        KeyMappingTrie.MatchResult r = trie.match(PADDED_ARROW_UP, PADDED_OFFSET, PADDED_LENGTH);
        bh.consume(r.action);
        bh.consume(r.hasPrefix);
    }

    @Benchmark
    public void f12(Blackhole bh) {
        KeyMappingTrie.MatchResult r = trie.match(F12);
        bh.consume(r.action);
        bh.consume(r.hasPrefix);
    }

    // ========== Unknown sequence ==========

    @Benchmark
    public void unknownCsi(Blackhole bh) {
        KeyMappingTrie.MatchResult r = trie.match(UNKNOWN_CSI);
        bh.consume(r.action);
        bh.consume(r.hasPrefix);
    }

    // ========== Throughput ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void singleByteThroughput(Blackhole bh) {
        KeyMappingTrie.MatchResult r = trie.matchSingleByte(CHAR_A);
        bh.consume(r);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void arrowKeyThroughput(Blackhole bh) {
        KeyMappingTrie.MatchResult r = trie.match(ARROW_UP);
        bh.consume(r);
    }
}
