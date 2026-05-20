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

import org.aesh.terminal.utils.ANSIBuilder;
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
 * Benchmarks for ANSIBuilder fluent ANSI string construction.
 * <p>
 * ANSIBuilder is used by aesh for help formatting, option rendering,
 * and styled text output. Each builder call appends ANSI escape
 * sequences to an internal StringBuilder.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Thread)
public class ANSIBuilderBenchmark {

    // ========== Simple text ==========

    @Benchmark
    public void plainText(Blackhole bh) {
        bh.consume(ANSIBuilder.builder().append("Hello World").toString());
    }

    @Benchmark
    public void boldText(Blackhole bh) {
        bh.consume(ANSIBuilder.builder().bold("Hello World").toString());
    }

    @Benchmark
    public void coloredText(Blackhole bh) {
        bh.consume(ANSIBuilder.builder().redText("Error").toString());
    }

    // ========== Compound styling ==========

    @Benchmark
    public void boldColoredText(Blackhole bh) {
        bh.consume(ANSIBuilder.builder()
                .bold().redText("Error: ").boldOff()
                .append("something went wrong")
                .toString());
    }

    @Benchmark
    public void multiColorLine(Blackhole bh) {
        bh.consume(ANSIBuilder.builder()
                .greenText("[OK] ")
                .append("Build ")
                .bold().cyanText("project-name").boldOff()
                .append(" completed in ")
                .yellowText("2.3s")
                .toString());
    }

    // ========== 256-color ==========

    @Benchmark
    public void color256Foreground(Blackhole bh) {
        bh.consume(ANSIBuilder.builder().color256(196, "ERROR").toString());
    }

    @Benchmark
    public void color256Both(Blackhole bh) {
        bh.consume(ANSIBuilder.builder()
                .color256(231).bg256(52).append(" CRITICAL ").reset()
                .toString());
    }

    // ========== True color (RGB) ==========

    @Benchmark
    public void rgbText(Blackhole bh) {
        bh.consume(ANSIBuilder.builder().rgb(255, 128, 0, "Warning").toString());
    }

    @Benchmark
    public void hexText(Blackhole bh) {
        bh.consume(ANSIBuilder.builder().hex("#FF8000", "Warning").toString());
    }

    // ========== Semantic colors ==========

    @Benchmark
    public void semanticError(Blackhole bh) {
        bh.consume(ANSIBuilder.builder().error("Something failed").toString());
    }

    @Benchmark
    public void semanticLogLine(Blackhole bh) {
        bh.consume(ANSIBuilder.builder()
                .timestamp("14:32:01 ")
                .info("[INFO] ")
                .category("com.example.App ")
                .message("Server started on port 8080")
                .toString());
    }

    // ========== Realistic aesh help output ==========

    @Benchmark
    public void helpOptionLine(Blackhole bh) {
        bh.consume(ANSIBuilder.builder()
                .append("  ")
                .bold().append("-o").boldOff()
                .append(", ")
                .bold().append("--output").boldOff()
                .append("=")
                .underline().append("FORMAT").underlineOff()
                .append("    Output format (json, yaml, text)")
                .toString());
    }

    @Benchmark
    public void helpBlock(Blackhole bh) {
        ANSIBuilder b = ANSIBuilder.builder();
        b.bold().append("Usage: ").boldOff().append("myapp [options] <command>").newline();
        b.newline();
        b.bold().append("Options:").boldOff().newline();
        b.append("  ").bold().append("-h").boldOff().append(", ")
                .bold().append("--help").boldOff().append("       Show help").newline();
        b.append("  ").bold().append("-v").boldOff().append(", ")
                .bold().append("--verbose").boldOff().append("    Verbose output").newline();
        b.append("  ").bold().append("-o").boldOff().append(", ")
                .bold().append("--output").boldOff().append("=")
                .underline().append("FMT").underlineOff().append("  Output format").newline();
        bh.consume(b.toString());
    }

    // ========== ANSI disabled (no-op path) ==========

    @Benchmark
    public void disabledBoldColorText(Blackhole bh) {
        bh.consume(ANSIBuilder.builder(false)
                .bold().redText("Error: ").boldOff()
                .append("something went wrong")
                .toString());
    }

    // ========== Throughput ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void multiColorLineThroughput(Blackhole bh) {
        bh.consume(ANSIBuilder.builder()
                .greenText("[OK] ")
                .append("completed")
                .toString());
    }
}
