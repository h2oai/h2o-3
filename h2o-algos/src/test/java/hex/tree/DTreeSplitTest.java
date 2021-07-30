package hex.tree;

import org.junit.Test;
import water.util.ArrayUtils;
import water.util.IcedBitSet;

import static org.junit.Assert.*;

public class DTreeSplitTest {

    @Test
    public void testIsNumericSplit() {
        assertFalse(newSplit(0, DHistogram.NASplitDir.NAvsREST, null, (byte) 0).isNumericSplit());
        assertTrue(newSplit(0, DHistogram.NASplitDir.NALeft, null, (byte) 0).isNumericSplit());
        assertTrue(newSplit(0, DHistogram.NASplitDir.NARight, new IcedBitSet(1), (byte) 0).isNumericSplit());
    }

    @Test
    public void testSplatNumeric() {
        double min = 20.0000000708;
        double maxIn = 20.0000009324;
        // 1. with check disabled
        DHistogram h = newHistogram(4, new double[]{min, maxIn}, false);
        float splat = newSplit(1, DHistogram.NASplitDir.NALeft, null, (byte) 0).splatNumeric(h);
        assertEquals(20f, splat, 0);
        // ouch both min and max go in one direction! => not an actual split
        assertTrue(min > splat);
        assertTrue(maxIn > splat);

        // 2. with check enabled
        DHistogram h1 = newHistogram(4, new double[]{min, maxIn}, true);
        float splat1 = newSplit(1, DHistogram.NASplitDir.NALeft, null, (byte) 0).splatNumeric(h1);
        // split should be abandoned
        assertTrue(Float.isNaN(splat1));
    }

    private DTree.Split newSplit(int bin, DHistogram.NASplitDir nasplit, IcedBitSet bs, byte equal) {
        return new DTree.Split(0, bin, nasplit, bs, equal, 
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    private DHistogram newHistogram(int nbins, double[] data, boolean checkFloatSplits) {
        double min = ArrayUtils.minValue(data);
        double max = ArrayUtils.maxValue(data);
        double maxEx = DHistogram.find_maxEx(max, 0);
        DHistogram histo = new DHistogram("histo" + nbins, nbins, 2, (byte) 0, min, maxEx, false, 0,
                SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 42L, null, null, checkFloatSplits);
        histo.init();
        histo.updateHisto(
                ArrayUtils.constAry(data.length, 1.0), 
                null,
                data,
                ArrayUtils.constAry(data.length, 1.0),
                null,
                ArrayUtils.seq(0, data.length),
                data.length,
                0);
        return histo;
    }

}
