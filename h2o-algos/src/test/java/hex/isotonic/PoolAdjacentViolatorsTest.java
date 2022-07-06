package hex.isotonic;

import org.junit.Before;
import org.junit.Test;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.util.ArrayUtils;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static water.TestUtil.ard;

import static org.junit.Assert.*;

public class PoolAdjacentViolatorsTest {

    private transient NewChunk[] ncs;

    @Before
    public void initNCS() {
        ncs = new NewChunk[]{new NewChunk(new double[0]), new NewChunk(new double[0]), new NewChunk(new double[0])};
    }

    @Test
    public void testConstantY() {
        PoolAdjacentViolators p = new PoolAdjacentViolators(ard(0.0, 0.0, 0.0));
        p.findThresholds(ard(0.1, 0.2, 0.3), ncs);
        assertArrayEquals(ard(
                0.0, 0.1, 1.5,
                0.0, 0.3, 1.5
        ), toMatrix(ncs), 0);
    }

    @Test
    public void testIncreasingY() {
        PoolAdjacentViolators p = new PoolAdjacentViolators(ard(0.0, 0.1, 0.2));
        p.findThresholds(ard(0.1, 0.2, 0.3), ncs);
        assertArrayEquals(ard(
                0.0, 0.1, 1.0, 
                0.1, 0.2, 1.0, 
                0.2, 0.3, 1.0
        ), toMatrix(ncs), 0);
    }

    @Test
    public void testDecreasingY() {
        PoolAdjacentViolators p = new PoolAdjacentViolators(ard(0.3, 0.2, 0.1));
        p.findThresholds(ard(0.1, 0.2, 0.3), ncs);
        assertArrayEquals(ard(
                0.2, 0.1, 1.5,
                0.2, 0.3, 1.5
        ), toMatrix(ncs), 1e-8);
    }

    @Test
    public void testAlternatingY() {
        PoolAdjacentViolators p = new PoolAdjacentViolators(ard(0.0, 1.0, 0.0));
        p.findThresholds(ard(0.1, 0.2, 0.3), ncs);
        assertArrayEquals(ard(
                0.0, 0.1, 1.0,
                0.5, 0.2, 1.0,
                0.5, 0.3, 1.0
        ), toMatrix(ncs), 0);
    }

    static double[] toMatrix(NewChunk[] ncs) {
        double[][] m = Stream.of(ncs)
                .map(NewChunk::compress)
                .map(Chunk::getDoubles)
                .collect(Collectors.toList())
                .toArray(new double[ncs.length][]);
        return Stream.of(ArrayUtils.transpose(m))
                .reduce(ArrayUtils::append)
                .orElseThrow(IllegalStateException::new);
    }

}
