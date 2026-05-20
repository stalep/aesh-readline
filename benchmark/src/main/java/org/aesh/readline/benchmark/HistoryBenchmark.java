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

import org.aesh.readline.history.InMemoryHistory;
import org.aesh.readline.history.SearchDirection;
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
 * Benchmarks for History search at realistic scales.
 * <p>
 * History.search() does linear O(n*m) scan via Parser.arrayContains
 * over all entries. CLI users accumulate hundreds to thousands of
 * history entries. This benchmark measures search performance at
 * various history sizes to identify scaling characteristics.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Thread)
public class HistoryBenchmark {

    @Param({"10", "100", "500", "1000"})
    private int historySize;

    // Search patterns
    private static final int[] SEARCH_SHORT = "ls".codePoints().toArray();
    private static final int[] SEARCH_MEDIUM = "git commit".codePoints().toArray();
    private static final int[] SEARCH_LONG = "docker run --rm -it".codePoints().toArray();
    private static final int[] SEARCH_MISS = "xyznonexistent".codePoints().toArray();

    private InMemoryHistory history;

    // Realistic command patterns
    private static final String[] COMMAND_TEMPLATES = {
            "ls -la /home/user/projects",
            "cd /var/log",
            "grep -rn 'error' *.log",
            "git status",
            "git add -A",
            "git commit -m 'fix: resolve issue'",
            "git push origin main",
            "mvn clean install -DskipTests",
            "docker ps -a",
            "docker run --rm -it ubuntu:22.04 bash",
            "kubectl get pods -n production",
            "ssh user@server.example.com",
            "cat /etc/hosts",
            "tail -f /var/log/syslog",
            "find . -name '*.java' -type f",
            "curl -s https://api.example.com/health",
            "python3 scripts/deploy.py --env staging",
            "npm run build",
            "make -j8 all",
            "vim ~/.bashrc"
    };

    @Setup(Level.Trial)
    public void setup() {
        history = new InMemoryHistory(historySize + 100);
        history.enable();

        // Fill with realistic commands
        for (int i = 0; i < historySize; i++) {
            String cmd = COMMAND_TEMPLATES[i % COMMAND_TEMPLATES.length];
            // Add variation to avoid exact duplicates
            if (i > COMMAND_TEMPLATES.length) {
                cmd = cmd + " # " + i;
            }
            history.push(cmd.codePoints().toArray());
        }
    }

    @Setup(Level.Invocation)
    public void resetSearch() {
        history.setSearchDirection(SearchDirection.REVERSE);
    }

    // ========== Search (reverse-i-search pattern) ==========

    @Benchmark
    public void searchShortHit(Blackhole bh) {
        bh.consume(history.search(SEARCH_SHORT));
    }

    @Benchmark
    public void searchMediumHit(Blackhole bh) {
        bh.consume(history.search(SEARCH_MEDIUM));
    }

    @Benchmark
    public void searchLongHit(Blackhole bh) {
        bh.consume(history.search(SEARCH_LONG));
    }

    @Benchmark
    public void searchMiss(Blackhole bh) {
        bh.consume(history.search(SEARCH_MISS));
    }

    // ========== Navigation (arrow up/down) ==========

    @Benchmark
    public void navigatePrevious(Blackhole bh) {
        bh.consume(history.previousFetch());
    }

    @Benchmark
    public void navigateNext(Blackhole bh) {
        // Navigate back first, then forward
        history.previousFetch();
        history.previousFetch();
        bh.consume(history.nextFetch());
    }

    // ========== Push (adding new entry) ==========

    @Benchmark
    public void pushEntry(Blackhole bh) {
        int[] entry = "new command entry".codePoints().toArray();
        history.push(entry);
        bh.consume(history.size());
    }

    // ========== Throughput ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void searchThroughput(Blackhole bh) {
        bh.consume(history.search(SEARCH_MEDIUM));
    }
}
