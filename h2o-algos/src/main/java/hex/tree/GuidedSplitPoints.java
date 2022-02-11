package hex.tree;

import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Implements a method for finding new histogram bins split-points based on a result of previous binning.
 * Idea:
 *   We take non-empty bins and look at the squared error they have. Based on the target bin number, we discard
 *   the empty bins and used the frees-up space to refine the non-non empty bins. Splitting of non-empty bins
 *   is guided by Squared Error accumulated in the bin. Bins with higher SE are split more than the bins with lower SE.
 *   Sub-bins (bins created from a single original bin) are refined uniformly.
 *   
 *   If uniform splitting fails in this iteration (= the distribution of values is significantly skewed), next iteration
 *   will attempt correct the issue by repeating the procedure with new bins (we are recursively refining the promising
 *   bins as we get deeper in the tree).
 */
public class GuidedSplitPoints {

    static final double LOW_DENSITY_THRESHOLD = 0.2;

    static boolean isApplicableTo(DHistogram h) {
        return h._vals != null &&  // observations were not yet binned, we don't have the data to guide the splitting
                h._isInt != 2 &&   // categorical columns have a specific handing
                !h._intOpt;        // integer optimized columns have a single value per bin, no point in refining such bins
    }

    static double[] makeSplitPoints(DHistogram h, final int targetNBins, final double min, final double maxEx) {
        // Collect bins to consider for refining
        final List<BinDescriptor> bins = extractNonEmptyBins(h, min, maxEx);

        // Budget is given by target number of bins in the new layer, we keep all non-empty bins
        final int totalBudget = targetNBins - bins.size() - 2; // how many bins we have to allocate (save 2 spots for min/max)
        if (bins.isEmpty() || totalBudget <= 0)
            return null;

        int budgetLeft = totalBudget; // how many bins do we have left to redistribute

        double totalSE = 0;
        for (BinDescriptor bin : bins) {
            totalSE += bin._se;
        }

        if (totalSE == 0)
            return null; // already perfect

        // For each bin find out how many new bins we can split it into
        int[] newBinCounts = new int[bins.size()];
        Collections.sort(bins); // sort by SE descending
        for (int b = 0; budgetLeft > 0 && b < newBinCounts.length; b++) {
            BinDescriptor bin = bins.get(b);
            // for integer columns it doesn't make sense to make the "in-bin" step size finer than 1
            int limit = h._isInt == 1 && bin.length() < Integer.MAX_VALUE ? 
                    Math.min(budgetLeft, (int) Math.round(bin.length())) : budgetLeft;
            // distribute proportionally to SE
            int newBins = Math.min((int) Math.ceil(totalBudget * bin._se / totalSE), limit);
            budgetLeft -= newBins;
            newBinCounts[b] = newBins;
            assert budgetLeft >= 0 : "We cannot go over allowed limit of bins!";
        }

        // Define new split-points
        final double[] customSplitPoints = new double[targetNBins - budgetLeft];
        int i = 0;
        for (int b = 0; b < newBinCounts.length; b++) {
            BinDescriptor bin = bins.get(b);
            customSplitPoints[i++] = bin._start;
            double stepSize = (bin._end - bin._start) / (1 + newBinCounts[b]);
            for (int s = 0; s < newBinCounts[b]; s++) {
                customSplitPoints[i] = customSplitPoints[i - 1] + stepSize;
                i++;
            }
        }
        customSplitPoints[i++] = min; // This is based on QuantilesGlobal - DHistogram has assumption min/max will be in the split-points
        customSplitPoints[i++] = h._maxIn;
        assert i == customSplitPoints.length;

        Arrays.sort(customSplitPoints);
        return ArrayUtils.makeUniqueAndLimitToRange(customSplitPoints, min, maxEx);
    }

    static List<BinDescriptor> extractNonEmptyBins(DHistogram h, double min, double maxEx) {
        final int nonEmptyBins = h.nonEmptyBins();
        final List<BinDescriptor> bins = new ArrayList<>(nonEmptyBins);
        for (int i = 0; i < h.nbins(); i++) {
            double weight = h.w(i);
            if (weight > 0) {
                BinDescriptor bin = BinDescriptor.fromBin(h, i);
                if ((bin._end < min) || (bin._start >= maxEx))
                    continue;
                bins.add(bin);
            }
        }
        return bins;
    }

    static class BinDescriptor implements Comparable<BinDescriptor> {
        final double _start;
        final double _end;
        final double _se;
        final double _weight;

        public BinDescriptor(double start, double end, double se, double weight) {
            _start = start;
            _end = end;
            _se = Math.max(se, 0); // rounding errors can cause SE to be negative
            _weight = weight;
        }

        double length() {
            return _end - _start;
        }

        @Override
        public int compareTo(BinDescriptor o) {
            return -Double.compare(_se, o._se);
        }

        static BinDescriptor fromBin(DHistogram h, int i) {
            double w = h.w(i);
            double wY = h.wY(i);
            double wYY = h.wYY(i);
            double se = w != 0 ? wYY - wY * wY / w : 0;
            return new BinDescriptor(h.binAt(i), h.binAt(i + 1), se, w);
        }

        // IntelliJ generated //

        @Override
        public String toString() {
            return "BinDescriptor{" +
                    "_start=" + _start +
                    ", _end=" + _end +
                    ", _se=" + _se +
                    ", _weight=" + _weight +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BinDescriptor that = (BinDescriptor) o;

            if (Double.compare(that._start, _start) != 0) return false;
            if (Double.compare(that._end, _end) != 0) return false;
            if (Double.compare(that._se, _se) != 0) return false;
            return Double.compare(that._weight, _weight) == 0;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = Double.doubleToLongBits(_start);
            result = (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(_end);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(_se);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(_weight);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }

}
