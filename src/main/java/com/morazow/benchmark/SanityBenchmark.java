package com.morazow.benchmark;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 10)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgsAppend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+UnlockExperimentalVMOptions",
        "--add-modules", "jdk.incubator.vector"
})
@Threads(1)
public class SanityBenchmark {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    @Param({"1024", "65536"})
    private int size;

    private float[] a, b, c;

    @Setup(Level.Iteration)
    public void setup() {
        a = new float[size];
        b = new float[size];
        c = new float[size];
        for (int i = 0; i < size; i++) {
            a[i] = ThreadLocalRandom.current().nextFloat();
            b[i] = ThreadLocalRandom.current().nextFloat();
            c[i] = 0;
        }
    }

    @Benchmark
    public void scalarComputation(Blackhole bh) {
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i] * a[i] + b[i] * b[i];
        }
        bh.consume(c);
    }

    @Benchmark
    public void vectorComputation(Blackhole bh) {
        int i = 0;
        int upperBound = SPECIES.loopBound(a.length);
        for (; i < upperBound; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            FloatVector vc = va.mul(va).add(vb.mul(vb));
            vc.intoArray(c, i);
        }
        for (; i < a.length; i++) {
            c[i] = a[i] * a[i] + b[i] * b[i];
        }
        bh.consume(c);
    }
}