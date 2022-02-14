package hex.tree;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class GuidedSplitPointsTest {

    @Test
    public void isApplicableTo_positive() {
        DHistogram histo = new DHistogram("yesPlease", 1000, 1024, (byte) 1, 0, 1000, false, false, -0.001,
                SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 42L, null, null, false, false, null, null);
        histo._vals = new double[100];

        assertTrue(GuidedSplitPoints.isApplicableTo(histo));
    }

    @Test
    public void isApplicableTo_intOptimized() {
        DHistogram histo = new DHistogram("intOptimized", 1000, 1024, (byte) 1, 0, 1000, true, false, -0.001,
                SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 42L, null, null, false, false, null, null);
        histo._vals = new double[0];

        assertFalse(GuidedSplitPoints.isApplicableTo(histo));
    }

    @Test
    public void isApplicableTo_categorical() {
        DHistogram histo = new DHistogram("categorical", 1000, 1024, (byte) 2, 0, 1000, false, false, -0.001,
                SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 42L, null, null, false, false, null, null);
        histo._vals = new double[0];

        assertFalse(GuidedSplitPoints.isApplicableTo(histo));
    }

    @Test
    public void isApplicableTo_notBinnedYet() {
        DHistogram histo = new DHistogram("undefined", 1000, 1024, (byte) 2, 0, 1000, false, false, -0.001,
                SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 42L, null, null, false, false, null, null);
        histo._vals = null;

        assertFalse(GuidedSplitPoints.isApplicableTo(histo));
    }

    @Test
    public void makeSplitPoints_tooSmall() {
        DHistogram h = makeSmallHistogram();
        double[] splitPoints = GuidedSplitPoints.makeSplitPoints(h, 4, h._min, h._maxEx);
        assertNull(splitPoints);
    }

    @Test
    public void makeSplitPoints_small() {
        DHistogram h = makeSmallHistogram();
        h._maxIn = 17;
        double[] splitPoints = GuidedSplitPoints.makeSplitPoints(h, 5, h._min, h._maxEx);
        assertArrayEquals(new double[]{0.0, 1.0, 1.5, 3.0, 17.0}, splitPoints, 0);
    }

    @Test
    public void makeSplitPoints() {
        DHistogram h = new DHistogram("numeric", 4, 1024, (byte) 0, 0, 101, false, false, -0.001,
                SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 42L, null, null, false, false, null, null);
        h._vals = new double[]{
                0, 0, 0, // empty
                0.3, 0.3 * 0.2, 0.3 * 0.2 * 0.2 * 2,
                0, 0, 0,
                0.1, 0.2, -1e-9,
                0, 0, 0 // slot for NA
        };

        double[] splitPoints = GuidedSplitPoints.makeSplitPoints(h, 13, h._min, h._maxEx);
        double[] expected = {
                0.0, 25.25, 27.775, 
                30.3, 32.8, 35.3, 37.8, 40.4, 42.9, 45.5, 47.9, // expanded 
                75.75};
        assertArrayEquals(expected, splitPoints, 0.1);
    }

    @Test
    public void extractNonEmptyBins() {
        DHistogram h = makeSmallHistogram();
        List<GuidedSplitPoints.BinDescriptor> bins = GuidedSplitPoints.extractNonEmptyBins(h);
        assertEquals(2, bins.size());
        assertEquals(Arrays.asList(
                GuidedSplitPoints.BinDescriptor.fromBin(h, 1),
                GuidedSplitPoints.BinDescriptor.fromBin(h, 3)
        ), bins);
    }

    @Test
    public void testBinDescriptorFromBin() {
        DHistogram histo = makeSmallHistogram();

        assertEquals(
                GuidedSplitPoints.BinDescriptor.fromBin(histo, 0), 
                new GuidedSplitPoints.BinDescriptor(0, 1, 0, 0)
        );

        assertEquals(
                GuidedSplitPoints.BinDescriptor.fromBin(histo, 1),
                new GuidedSplitPoints.BinDescriptor(1, 2, 0.012, 0.3)
        );

        assertEquals(
                GuidedSplitPoints.BinDescriptor.fromBin(histo, 3),
                new GuidedSplitPoints.BinDescriptor(3, 4, 0, 0.1)
        );
    }

    private DHistogram makeSmallHistogram() {
        DHistogram histo = new DHistogram("numeric", 1000, 1024, (byte) 0, 0, 1000, false, false, -0.001,
                SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 42L, null, null, false, false, null, null);
        histo._nbin = 4;
        histo._vals = new double[]{
                0, 0, 0, // empty
                0.3, 0.3 * 0.2, 0.3 * 0.2 * 0.2 * 2,
                0, 0, 0,
                0.1, 0.2, -1e-9,
                0, 0, 0 // slot for NA
        };
        return histo;
    }
    
}
