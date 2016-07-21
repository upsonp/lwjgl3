/*
 * Copyright LWJGL. All rights reserved.
 * License terms: http://lwjgl.org/license.php
 */
package org.lwjgl.jmh;

import org.lwjgl.*;
import org.lwjgl.system.*;
import org.openjdk.jmh.annotations.*;

import java.nio.*;

import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.jemalloc.JEmalloc.*;
import static org.lwjgl.system.libc.LibCStdlib.*;
import static org.lwjgl.system.rpmalloc.RPmalloc.*;

/**
 * Windows 10, Ryzen 1800X, JDK 8u141
 *
 * Threads = 1
 * Benchmark         Mode  Cnt    Score   Error  Units
 * nio               avgt    5  153,964 ±  6,202  ns/op
 *
 * malloc            avgt    5   62,410 ±  1,269  ns/op
 * calloc            avgt    5   68,901 ± 18,732  ns/op
 * aligned_alloc     avgt    5   68,094 ±  0,908  ns/op
 *
 * je_malloc         avgt    5   74,249 ±  0,432  ns/op
 * je_calloc         avgt    5   78,237 ±  1,130  ns/op
 * je_aligned_alloc  avgt    5   84,573 ±  1,215  ns/op
 *
 * rpmalloc          avgt    5   22,758 ±  0,112  ns/op
 * rpcalloc          avgt    5   28,628 ±  0,368  ns/op
 * rpaligned_alloc   avgt    5   24,697 ±  0,398  ns/op
 *
 * stack_malloc      avgt    5    4,882 ±  0,191  ns/op
 * stack_calloc      avgt    5   12,221 ±  1,376  ns/op
 * stack_aligned_all avgt    5    4,922 ±  0,721  ns/op
 *
 * Threads = 8
 * Benchmark         Mode  Cnt     Score    Error  Units
 * nio               avgt    5  2635,975 ± 30,541  ns/op
 *
 * malloc            avgt    5    64,484 ±  3,072  ns/op
 * calloc            avgt    5    72,425 ±  3,452  ns/op
 * aligned_alloc     avgt    5   107,290 ± 66,822  ns/op
 *
 * je_malloc         avgt    5    73,671 ±  3,060  ns/op
 * je_calloc         avgt    5    91,945 ± 24,119  ns/op
 * je_aligned_alloc  avgt    5   101,759 ± 51,296  ns/op
 *
 * rpmalloc          avgt    5    24,270 ±  1,952  ns/op
 * rpcalloc          avgt    5    30,767 ±  1,326  ns/op
 * rpaligned_alloc   avgt    5    26,059 ±  1,650  ns/op
 *
 * Linux (Ubuntu VM), i7-5557U (Broadwell), JDK 8u91
 *
 * Threads = 2
 * Benchmark         Mode  Cnt    Score     Error  Units
 * nio               avgt    5  1089.062 ± 219.510  ns/op
 *
 * malloc            avgt    5   182.453 ±  36.738  ns/op
 * calloc            avgt    5   181.586 ±  35.411  ns/op
 * aligned_alloc     avgt    5   226.208 ±  12.248  ns/op
 *
 * je_malloc         avgt    5    57.651 ±   5.887  ns/op
 * je_calloc         avgt    5    64.396 ±   8.389  ns/op
 * je_aligned_alloc  avgt    5    68.294 ±  46.954  ns/op
 *
 * rpmalloc          avgt    5    51.511 ±   9.855  ns/op
 * rpcalloc          avgt    5    62.516 ±  10.403  ns/op
 * rpaligned_alloc   avgt    5    55.188 ±   3.199  ns/op
 *
 * macOS, i7-5557U (Broadwell), JDK 8u112
 *
 * Threads = 2
 * Benchmark         Mode  Cnt    Score     Error  Units
 * nio               avgt    5  459.305 ±  29.294  ns/op
 *
 * malloc            avgt    5  105.673 ±  10.183  ns/op
 * calloc            avgt    5  117.355 ±   2.655  ns/op
 * aligned_alloc     avgt    5  340.798 ± 209.297  ns/op
 *
 * je_malloc         avgt    5   45.460 ±   2.599  ns/op
 * je_calloc         avgt    5   51.303 ±   1.855  ns/op
 * je_aligned_alloc  avgt    5   52.702 ±   4.020  ns/op
 *
 * rpmalloc          avgt    5   47.653 ±   0.328  ns/op
 * rpcalloc          avgt    5   54.836 ±   6.808  ns/op
 * rpaligned_alloc   avgt    5   50.272 ±   3.148  ns/op
 */
@Threads(8)
@State(Scope.Thread)
public class MallocTest {

    private static final int SIZE = 128;

    private static final int ALIGNMENT = 32;

    static {
        rpmalloc_initialize();
    }

    @Setup(Level.Trial)
    public void rpmallocInitThread() {
        rpmalloc_thread_initialize();
    }

    @Benchmark
    public void t00_nio() {
        ByteBuffer mem = BufferUtils.createByteBuffer(SIZE);
        ((sun.nio.ch.DirectBuffer)mem).cleaner().clean();
    }

    @Benchmark
    public void t10_malloc() {
        ByteBuffer mem = malloc(SIZE);
        free(mem);
    }

    @Benchmark
    public void t11_calloc() {
        ByteBuffer mem = calloc(1, SIZE);
        free(mem);
    }

    @Benchmark
    public void t12_aligned_alloc() {
        ByteBuffer mem = aligned_alloc(ALIGNMENT, SIZE);
        aligned_free(mem);
    }

    @Benchmark
    public void t20_je_malloc() {
        ByteBuffer mem = je_malloc(SIZE);
        je_free(mem);
    }

    @Benchmark
    public void t21_je_calloc() {
        ByteBuffer mem = je_calloc(1, SIZE);
        je_free(mem);
    }

    @Benchmark
    public void t23_je_aligned_alloc() {
        ByteBuffer mem = je_aligned_alloc(ALIGNMENT, SIZE);
        je_free(mem);
    }

    @Benchmark
    public void t30_rpmalloc() {
        ByteBuffer mem = rpmalloc(SIZE);
        rpfree(mem);
    }

    @Benchmark
    public void t31_rpcalloc() {
        ByteBuffer mem = rpcalloc(1, SIZE);
        rpfree(mem);
    }

    @Benchmark
    public void t32_rpaligned_alloc() {
        ByteBuffer mem = rpaligned_alloc(ALIGNMENT, SIZE);
        rpfree(mem);
    }

    @Benchmark
    public void t40_stack_malloc() {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer mem = stack.malloc(SIZE);
        }
    }

    @Benchmark
    public void t41_stack_calloc() {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer mem = stack.calloc(SIZE);
        }
    }

    @Benchmark
    public void t42_stack_aligned_alloc() {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer mem = stack.malloc(ALIGNMENT, SIZE);
        }
    }

}
