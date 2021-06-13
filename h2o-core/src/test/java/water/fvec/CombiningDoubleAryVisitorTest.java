package water.fvec;

import org.junit.Test;

import static org.junit.Assert.*;

public class CombiningDoubleAryVisitorTest {

    @Test
    public void testAddValue_int() {
        double[] ary = new double[]{-2, -4, -6};
        ChunkVisitor.CombiningDoubleAryVisitor v = new ChunkVisitor.CombiningDoubleAryVisitor(ary);
        v.addValue(1);
        v.addValue(2);
        v.addValue(3);
        assertArrayEquals(new double[]{-1, -2, -3}, ary, 0);
    }

    @Test
    public void testAddValue_long() {
        double[] ary = new double[]{-2, -4, -6};
        ChunkVisitor.CombiningDoubleAryVisitor v = new ChunkVisitor.CombiningDoubleAryVisitor(ary);
        v.addValue(1L);
        v.addValue(2L);
        v.addValue(3L);
        assertArrayEquals(new double[]{-1, -2, -3}, ary, 0);
    }

    @Test
    public void testAddValue_double() {
        double[] ary = new double[]{-0.1d, Double.NaN, -0.6d};
        ChunkVisitor.CombiningDoubleAryVisitor v = new ChunkVisitor.CombiningDoubleAryVisitor(ary);
        v.addValue(1.1d);
        v.addValue(2.2d);
        v.addValue(3.3d);
        assertArrayEquals(new double[]{1.0,Double.NaN,2.7}, ary, 1e-8);
    }

    @Test
    public void testAddZeros() {
        double[] ary = new double[]{-2, Double.NaN, -6};
        ChunkVisitor.CombiningDoubleAryVisitor v = new ChunkVisitor.CombiningDoubleAryVisitor(ary);
        v.addZeros(2);
        v.addValue(10);
        assertArrayEquals(new double[]{-2, Double.NaN, 4}, ary, 0);
    }

    @Test
    public void testAddNAs() {
        double[] ary = new double[]{Double.NaN, 0, -6};
        ChunkVisitor.CombiningDoubleAryVisitor v = new ChunkVisitor.CombiningDoubleAryVisitor(ary);
        v.addNAs(2);
        v.addValue(10);
        assertArrayEquals(new double[]{Double.NaN, Double.NaN, 4}, ary, 0);
    }

}
