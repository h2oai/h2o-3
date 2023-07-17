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
        String categories = String.join("", _bins.stream().map(b -> String.valueOf(((CategoricalBin) b)._category)).collect(Collectors.toSet()));
        Set<String> splits = findAllCategoricalSplits(categories);
//        Set<BinAccumulatedStatistics> statistics = findAllCategoricalSplits(categories);
        return null;
    }
    
    private Set<String> findAllCategoricalSplits(String categories) { // todo - test it
        int recMaxDepth = categories.length() / 2; // floor. Generate only one half of the subsets as the rest is the complement
        Set<String> masks = new HashSet<>();
        // for 1 to recMaxDepth - 1 depth generate all options
        for(int depth = 1; depth < recMaxDepth; depth++) {
            for(String s: categories.split("")) {
                // use substring method to clone (deep copy) the string
                rec(masks, s, categories.substring(0).replaceAll(s, ""), depth - 1);
            }
        }
        // for recMaxDepth - 1 depth (the highest) generate only half of the options for the even length and all options for the odd
        if(categories.length() == recMaxDepth * 2) {
            // try only one first category
            rec(masks, categories.substring(0, 1), categories.substring(1), recMaxDepth - 1);
        } // nope: should be covered if generate only half of the depth and half of the combination (include first value / complementary will exclude it)
         else for(String s: categories.split("")) {
            // use substring method to clone (deep copy) the string
            rec(masks, s, categories.substring(0).replaceAll(s, ""), recMaxDepth - 1);
        }
//        List<String> res = masks.stream().sorted().collect(Collectors.toList());
        return masks;
    }

    private void rec(Set<String> masks, String current, String categories, int stepsToGo) {
        if (stepsToGo == 0) {
            masks.add(current);
            return;
        }
        for(String s: categories.split("")) {
            if(s.charAt(0) > current.charAt(current.length()-1))
                // use substring method to clone (deep copy) the string
                rec(masks, current+s, categories.substring(0).replaceAll(s, ""), stepsToGo - 1);
        }
        
    }
}
