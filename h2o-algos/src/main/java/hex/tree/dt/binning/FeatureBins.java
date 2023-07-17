package hex.tree.dt.binning;

import hex.tree.dt.NumericSplittingRule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FeatureBins {

    private List<AbstractBin> _bins;
    private final boolean _isConstant;  // todo - test this

    public FeatureBins(List<AbstractBin> bins) {
        if (bins == null) {
            _isConstant = true;
        } else {
            _isConstant = false;
            _bins = bins;
        }
    }

    /**
     * Calculates statistics for bins depending on all other bins - see BinAccumulatedStatistics.
     *
     * @return list of accumulated statistics, matches original bins
     */
    public List<BinAccumulatedStatistics> calculateBinsStatisticsForNumericFeature() {
        // init list with empty instances
        List<BinAccumulatedStatistics> statistics = _bins.stream()
                .map(b -> new BinAccumulatedStatistics()).collect(Collectors.toList());
        // calculate accumulative statistics for each bin: 
        // left split - bins to the left + current; 
        // right split - bins to the right.
        BinAccumulatedStatistics tmpAccumulatorLeft = new BinAccumulatedStatistics();
        BinAccumulatedStatistics tmpAccumulatorRight = new BinAccumulatedStatistics();
        int rightIndex;
        for (int leftIndex = 0; leftIndex < statistics.size(); leftIndex++) {
            tmpAccumulatorLeft.accumulateLeftStatistics(_bins.get(leftIndex)._count0, _bins.get(leftIndex)._count);
            statistics.get(leftIndex).copyLeftValues(tmpAccumulatorLeft);
            statistics.get(leftIndex)._splittingRule = new NumericSplittingRule(((NumericBin) _bins.get(leftIndex))._max);
            // accumulate from the right (from the end of bins array)
            rightIndex = _bins.size() - leftIndex - 1;
            // firstly accumulate with old values, then add the actual bin for the future statistics 
            // as the values of the actual bin are not included in its right statistics
            statistics.get(rightIndex).copyRightValues(tmpAccumulatorRight);
            tmpAccumulatorRight.accumulateRightStatistics(_bins.get(rightIndex)._count0,
                    _bins.get(rightIndex)._count);
        }
        return statistics;
    }

    public boolean isConstant() {
        return _isConstant;
    }

    List<AbstractBin> getFeatureBins() {
        return _bins.stream().map(AbstractBin::clone).collect(Collectors.toList());
    }

    public List<BinAccumulatedStatistics> calculateBinsStatisticsForCategoricalFeature() {
        // calculate accumulative statistics for each subset of categories: 
        // left split - categories included in the subset; 
        // right split - categories not included in subset.
        // calculate criterion for each splitting point (bin)
        // todo
        // init list with empty instances
//        String categories = String.join("", _bins.stream().map(b -> String.valueOf(((CategoricalBin) b)._category)).collect(Collectors.toSet()));
//        Set<String> splits = findAllCategoricalSplits(categories);
//        Set<BinAccumulatedStatistics> statistics = findAllCategoricalSplits(categories);
        return null;
    }
    
}
