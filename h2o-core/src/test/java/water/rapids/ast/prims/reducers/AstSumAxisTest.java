package water.rapids.ast.prims.reducers;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.TestUtilSharedResources;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import java.util.ArrayList;

import static org.junit.Assert.*;


/**
 * Test the AstSumAxis.java class
 */
public class AstSumAxisTest extends TestUtilSharedResources {
    private static Vec vi1, vd1, vd2, vd3, vs1, vt1, vt2, vc1, vc2;
    private static ArrayList<Frame> allFrames;

    @BeforeClass public static void setup() {
        stall_till_cloudsize(1);
        vi1 = TestUtil.ivec(-1, -2, 0, 2, 1);
        vd1 = TestUtil.dvec(1.5, 2.5, 3.5, 4.5, 8.0);
        vd2 = TestUtil.dvec(0.2, 0.4, 0.6, 0.8, 1.0);
        vd3 = TestUtil.dvec(1, 2, Double.NaN, 3, Double.NaN);
        vs1 = TestUtil.svec("a", "b", "c", "d", "e");
        vt1 = TestUtil.tvec(10000000, 10000020, 10000030, 10000040, 10000060);
        vt2 = TestUtil.tvec(20000000, 20000020, 20000030, 20000040, 20000060);
        vc1 = TestUtil.cvec(ar("N", "Y"), "Y", "N", "Y", "Y", "N");
        vc2 = TestUtil.cvec("a", "c", "c", "b", "a");
        allFrames = new ArrayList<>(10);
    }

    @AfterClass public static void teardown() {
        for (Vec v : aro(vi1, vd1, vd2, vd3, vs1, vt1, vt2, vc1, vc2))
            v.remove();
        for (Frame f : allFrames)
            f.delete();
    }

    //--------------------------------------------------------------------------------------------------------------------
    // Tests
    //--------------------------------------------------------------------------------------------------------------------

    @Test public void testAstSumGeneralStructure() {
        AstSumAxis a = new AstSumAxis();
        String[] args = a.args();
        assertEquals(3, args.length);
        String example = a.example();
        assertTrue(example.startsWith("(sumaxis "));
        String description = a.description();
        assertTrue("Description for AstSum is too short", description.length() > 100);
    }

    @Test public void testColumnwisesumWithoutNaRm() {
        Frame fr = register(new Frame(Key.<Frame>make(),
                ar("I", "D", "DD", "DN", "T", "S", "C"),
                aro(vi1, vd1, vd2, vd3, vt1, vs1, vc2)
        ));
        Val val1 = Rapids.exec("(sumaxis " + fr._key + " 0 0)");
        assertTrue(val1 instanceof ValFrame);
        Frame res = register(val1.getFrame());
        assertArrayEquals(fr.names(), res.names());
        assertArrayEquals(ar(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_TIME, Vec.T_NUM, Vec.T_NUM), res.types());
        assertRowFrameEquals(ard(0.0, 20.0, 3.0, Double.NaN, 50000150.0, Double.NaN, Double.NaN), res);
    }

    @Test public void testColumnwiseSumWithNaRm() {
        Frame fr = register(new Frame(Key.<Frame>make(),
                ar("I", "D", "DD", "DN", "T", "S", "C"),
                aro(vi1, vd1, vd2, vd3, vt1, vs1, vc2)
        ));
        Val val = Rapids.exec("(sumaxis " + fr._key + " 1 0)");
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertArrayEquals(fr.names(), res.names());
        assertArrayEquals(ar(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_TIME, Vec.T_NUM, Vec.T_NUM), res.types());
        assertRowFrameEquals(ard(0.0, 20.0, 3.0, 6.0, 50000150.0, Double.NaN, Double.NaN), res);
    }

    @Test public void testColumnwisesumOnEmptyFrame() {
        Frame fr = register(new Frame(Key.<Frame>make()));
        Val val = Rapids.exec("(sumaxis " + fr._key + " 0 0)");
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertEquals(res.numCols(), 0);
        assertEquals(res.numRows(), 0);
    }

    @Test public void testColumnwisesumBinaryVec() {
        assertTrue(vc1.isBinary() && !vc2.isBinary());
        Frame fr = register(new Frame(Key.<Frame>make(), ar("C1", "C2"), aro(vc1, vc2)));
        Val val = Rapids.exec("(sumaxis " + fr._key + " 1 0)");
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertArrayEquals(fr.names(), res.names());
        assertArrayEquals(ar(Vec.T_NUM, Vec.T_NUM), res.types());
        assertRowFrameEquals(ard(3.0, Double.NaN), res);
    }

    @Test public void testRowwisesumWithoutNaRm() {
        Frame fr = register(new Frame(Key.<Frame>make(),
                ar("i1", "d1", "d2", "d3"),
                aro(vi1, vd1, vd2, vd3)
        ));
        Val val = Rapids.exec("(sumaxis " + fr._key + " 0 1)");
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertColFrameEquals(ard(1.7, 2.9, Double.NaN, 10.3, Double.NaN), res);
        assertEquals("sum", res.name(0));
    }

    @Test public void testRowwisesumWithoutNaRmAndNonnumericColumn() {
        Frame fr = register(new Frame(Key.<Frame>make(),
                ar("i1", "d1", "d2", "d3", "s1"),
                aro(vi1, vd1, vd2, vd3, vs1)
        ));
        Val val = Rapids.exec("(sumaxis " + fr._key + " 0 1)");
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertColFrameEquals(ard(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN), res);
        assertEquals("sum", res.name(0));
    }

    @Test public void testRowwisesumWithNaRm() {
        Frame fr = register(new Frame(Key.<Frame>make(),
                ar("i1", "d1", "d2", "d3", "s1"),
                aro(vi1, vd1, vd2, vd3, vs1)
        ));
        Val val = Rapids.exec("(sumaxis " + fr._key + " 1 1)");
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertEquals("Unexpected column name", "sum", res.name(0));
        assertEquals("Unexpected column type", Vec.T_NUM, res.types()[0]);
        assertColFrameEquals(ard(1.7, 2.9, 4.1, 10.3, 10.0), res);
    }

    @Test public void testRowwisesumOnFrameWithTimeColumnsOnly() {
        Frame fr = register(new Frame(Key.<Frame>make(), ar("t1", "s", "t2"), aro(vt1, vs1, vt2)));
        Val val = Rapids.exec("(sumaxis " + fr._key + " 1 1)");
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertEquals("Unexpected column name", "sum", res.name(0));
        assertEquals("Unexpected column type", Vec.T_TIME, res.types()[0]);
        assertColFrameEquals(ard(30000000, 30000040, 30000060, 30000080, 30000120), res);
    }

    @Test public void testRowwisesumOnFrameWithTimeandNumericColumn() {
        Frame fr = register(new Frame(Key.<Frame>make(), ar("t1", "i1"), aro(vt1, vi1)));
        Val val = Rapids.exec("(sumaxis " + fr._key + " 1 1)");
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertColFrameEquals(ard(-1, -2, 0, 2, 1), res);
    }

    @Test public void testRowwisesumOnEmptyFrame() {
        Frame fr = register(new Frame(Key.<Frame>make()));
        Val val = Rapids.exec("(sumaxis " + fr._key + " 0 1)");
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertEquals(res.numCols(), 0);
        assertEquals(res.numRows(), 0);
    }

    @Test public void testRowwisesumOnFrameWithNonnumericColumnsOnly() {
        Frame fr = register(new Frame(Key.<Frame>make(), ar("c1", "s1"), aro(vc2, vs1)));
        Val val = Rapids.exec("(sumaxis " + fr._key + " 1 1)");
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertEquals("Unexpected column name", "sum", res.name(0));
        assertEquals("Unexpected column type", Vec.T_NUM, res.types()[0]);
        assertColFrameEquals(ard(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN), res);
    }

    @Test public void testBadFirstArgument() {
        try {
            Rapids.exec("(sumaxis " + vi1._key + " 1 0)");
            fail();
        } catch (IllegalArgumentException ignored) {}
        try {
            Rapids.exec("(sum hello 1 0)");
            fail();
        } catch (IllegalArgumentException ignored) {}
        try {
            Rapids.exec("(sum 2 1 0)");
            fail();
        } catch (IllegalArgumentException ignored) {}
    }

    @Test public void testValRowArgument() {
        Frame fr = register(new Frame(Key.<Frame>make(),
                ar("i1", "d1", "d2", "d3"),
                aro(vi1, vd1, vd2, vd3)
        ));
        Val val = Rapids.exec("(apply " + fr._key + " 1 {x . (sumaxis x 1)})");  // skip NAs
        assertTrue(val instanceof ValFrame);
        Frame res = register(val.getFrame());
        assertColFrameEquals(ard(1.7, 2.9, 4.1, 10.3, 10.0), res);

        Val val2 = Rapids.exec("(apply " + fr._key + " 1 {x . (sumaxis x 0)})");  // do not skip NAs
        assertTrue(val2 instanceof ValFrame);
        Frame res2 = register(val2.getFrame());
        assertColFrameEquals(ard(1.7, 2.9, Double.NaN, 10.3, Double.NaN), res2);
    }


    //--------------------------------------------------------------------------------------------------------------------
    // Helpers
    //--------------------------------------------------------------------------------------------------------------------

    private static void assertRowFrameEquals(double[] expected, Frame actual) {
        assertEquals(1, actual.numRows());
        assertEquals(expected.length, actual.numCols());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Wrong sum in column " + actual.name(i), expected[i], actual.vec(i).at(0), 1e-8);
        }
    }

    private static void assertColFrameEquals(double[] expected, Frame actual) {
        assertEquals(1, actual.numCols());
        assertEquals(expected.length, actual.numRows());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Wrong sum in row " + i, expected[i], actual.vec(0).at(i), 1e-8);
        }
    }

    private static Frame register(Frame f) {
        if (f._key != null) DKV.put(f._key, f);
        allFrames.add(f);
        return f;
    }

}
