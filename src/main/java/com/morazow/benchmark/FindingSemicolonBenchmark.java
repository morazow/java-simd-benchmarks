package com.morazow.benchmark;

import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 10)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgsAppend = { //
        "-XX:+UnlockDiagnosticVMOptions", //
        "-XX:+UnlockExperimentalVMOptions", //
        "--add-modules", "jdk.incubator.vector" //
})
@Threads(1)
public class FindingSemicolonBenchmark {

    @State(Scope.Benchmark)
    public static class Measurements {
        public byte[] bytes;
        public List<Integer> semicolonPositions;

        @Param({"measurements-100K.txt"})
        String filename;

        @Setup(Level.Trial)
        public void setup() {
            bytes = readFile();
            semicolonPositions = findSemicolonPositions(bytes);
        }

        private byte[] readFile() {
            try {
                return Files.readAllBytes(Path.of("src/main/resources/1BRC/", filename));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private List<Integer> findSemicolonPositions(byte[] bytes) {
            final List<Integer> results = new ArrayList<>();
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == ';') {
                    results.add(i);
                }
            }
            return results;
        }
    }

    @Benchmark
    public void linearScan(Measurements measurements) {
        int numOfSemicolons = 0;
        for (int i = 0; i < measurements.bytes.length; i++) {
            if (measurements.bytes[i] == ';') {
                numOfSemicolons += 1;
            }
        }
        if (numOfSemicolons != measurements.semicolonPositions.size()) {
            throw new AssertionError();
        }
    }

    private static final long SEMICOLON_PATTERN = 0x3B3B3B3B3B3B3B3BL;

    private static int findFirstByteLamport(long word) {
        long input = word ^ SEMICOLON_PATTERN;
        long mask = (input & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL;
        mask = ~(mask | input | 0x7F7F7F7F7F7F7F7FL);
        return Long.numberOfTrailingZeros(mask) >>> 3;
    }

    @Benchmark
    public void swarLamport(Measurements measurements) {
        int numOfSemicolons = 0;
        final ByteBuffer byteBuffer = ByteBuffer.wrap(measurements.bytes).order(ByteOrder.LITTLE_ENDIAN);
        int i = 0;
        while (i + Long.BYTES <= measurements.bytes.length) {
            long word = byteBuffer.getLong(i);
            int index = findFirstByteLamport(word);
            if (index < Long.BYTES) {
                i += index + 1;
                numOfSemicolons += 1;
            } else {
                i += Long.BYTES;
            }
        }
        for (; i < measurements.bytes.length; i++) {
            if (measurements.bytes[i] == ';') {
                numOfSemicolons += 1;
            }
        }
        if (numOfSemicolons != measurements.semicolonPositions.size()) {
            throw new AssertionError();
        }
    }

    private static int findFirstByteMycroft(long word) {
        final long match = word ^ SEMICOLON_PATTERN;
        long mask = (match - 0x0101010101010101L) & ~match & 0x8080808080808080L;
        return Long.numberOfTrailingZeros(mask) >>> 3;
    }

    @Benchmark
    public void swarMycroft(Measurements measurements) {
        int numOfSemicolons = 0;
        final ByteBuffer byteBuffer = ByteBuffer.wrap(measurements.bytes).order(ByteOrder.LITTLE_ENDIAN);
        int i = 0;
        while (i + Long.BYTES <= measurements.bytes.length) {
            long word = byteBuffer.getLong(i);
            int index = findFirstByteMycroft(word);
            if (index < Long.BYTES) {
                i += index + 1;
                numOfSemicolons += 1;
            } else {
                i += Long.BYTES;
            }
        }
        for (; i < measurements.bytes.length; i++) {
            if (measurements.bytes[i] == ';') {
                numOfSemicolons += 1;
            }
        }
        if (numOfSemicolons != measurements.semicolonPositions.size()) {
            throw new AssertionError();
        }
    }

    private static final VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;

    @Benchmark
    public void vectorAPI(Measurements measurements) {
        int numOfSemicolons = 0;
        Vector<Byte> semicolonVector = BYTE_SPECIES.broadcast(';');
        int vectorLoopBound = BYTE_SPECIES.loopBound(measurements.bytes.length);
        int byteSpeciesSize = BYTE_SPECIES.vectorByteSize();
        int i = 0;
        for (; i < vectorLoopBound; i += byteSpeciesSize) {
            Vector<Byte> vector = ByteVector.fromArray(BYTE_SPECIES, measurements.bytes, i);
            VectorMask<Byte> mask = vector.compare(VectorOperators.EQ, semicolonVector);
            numOfSemicolons += mask.trueCount();
        }
        while (i < measurements.bytes.length) {
            if (measurements.bytes[i] == ';') {
                numOfSemicolons += 1;
            }
            i++;
        }
        if (numOfSemicolons != measurements.semicolonPositions.size()) {
            throw new AssertionError();
        }
    }

}