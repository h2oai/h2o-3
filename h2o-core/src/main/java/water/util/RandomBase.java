package water.util;

import java.util.Random;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class RandomBase extends Random {

    protected RandomBase() {
        super();
    }

    protected RandomBase(long seed) {
        super(seed);
    }

    /**
     * Returns a pseudorandom, uniformly distributed value
     * between 0 (inclusive) and the specified value (exclusive).
     *
     * @see jsr166y.ThreadLocalRandom#nextLong(long) 
     * 
     * @param n the bound on the random number to be returned.  Must be
     *        positive.
     * @return the next value
     * @throws IllegalArgumentException if n is not positive
     */
    public long nextLong(long n) {
        if (n <= 0)
            throw new IllegalArgumentException("n must be positive");
        // Divide n by two until small enough for nextInt. On each
        // iteration (at most 31 of them but usually much less),
        // randomly choose both whether to include high bit in result
        // (offset) and whether to continue with the lower vs upper
        // half (which makes a difference only if odd).
        long offset = 0;
        while (n >= Integer.MAX_VALUE) {
            int bits = next(2);
            long half = n >>> 1;
            long nextn = ((bits & 2) == 0) ? half : n - half;
            if ((bits & 1) == 0)
                offset += n - nextn;
            n = nextn;
        }
        return offset + nextInt((int) n);
    }

    @Override
    public final IntStream ints(long streamSize) {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final IntStream ints() {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final IntStream ints(int randomNumberOrigin, int randomNumberBound) {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final LongStream longs(long streamSize) {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final LongStream longs() {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final LongStream longs(long randomNumberOrigin, long randomNumberBound) {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final DoubleStream doubles(long streamSize) {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final DoubleStream doubles() {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final DoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }

    @Override
    public final DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
        throw new UnsupportedOperationException("Please avoid using Stream API on Random - it introduces different behavior on different Java versions");
    }
}
