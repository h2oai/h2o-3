package hex.tree.dt.binning;

import hex.tree.dt.CategoricalSplittingRule;
import hex.tree.dt.NumericSplittingRule;

import java.util.*;
import java.util.stream.Collectors;

public class FeatureBins {

    private List<AbstractBin> _bins;
    private final boolean _isConstant;
    private int _numOfCategories;

    public FeatureBins(List<AbstractBin> bins) {
        // default value of numOfCategories is -1 (used for the numeric features)
        this(bins, -1);
    }

    public FeatureBins(List<AbstractBin> bins, int numOfCategories) {
        if (bins == null) {
            _isConstant = true;
        } else {
            _isConstant = false;
            _bins = bins;
            _numOfCategories = numOfCategories;
        }
    }

    /**
     * Calculates statistics for bins depending on all other bins - see BinAccumulatedStatistics.
     *
     * @return list of accumulated statistics, matches original bins
     */
    public List<SplitStatistics> calculateSplitStatisticsForNumericFeature(int nclass) {
        // init list with empty instances
        List<SplitStatistics> statistics = _bins.stream()
                .map(b -> new SplitStatistics(nclass)).collect(Collectors.toList());
        // calculate accumulative statistics for each split: 
        // left split - bins to the left + current; 
        // right split - bins to the right.
        SplitStatistics tmpAccumulatorLeft = new SplitStatistics(nclass);
        SplitStatistics tmpAccumulatorRight = new SplitStatistics(nclass);
        int rightIndex;
        for (int leftIndex = 0; leftIndex < statistics.size(); leftIndex++) {
            tmpAccumulatorLeft.accumulateLeftStatistics(_bins.get(leftIndex)._count, _bins.get(leftIndex)._classesDistribution);
            statistics.get(leftIndex).copyLeftValues(tmpAccumulatorLeft);
            statistics.get(leftIndex)._splittingRule = new NumericSplittingRule(((NumericBin) _bins.get(leftIndex))._max);
            // accumulate from the right (from the end of bins array)
            rightIndex = _bins.size() - leftIndex - 1;
            // firstly accumulate with old values, then add the actual bin for the future statistics 
            // as the values of the actual bin are not included in its right statistics
            statistics.get(rightIndex).copyRightValues(tmpAccumulatorRight);
            tmpAccumulatorRight.accumulateRightStatistics(_bins.get(rightIndex)._count, _bins.get(rightIndex)._classesDistribution);
        }
        return statistics;
    }

    public boolean isConstant() {
        return _isConstant;
    }

    List<AbstractBin> getFeatureBins() {
        return _bins.stream().map(AbstractBin::clone).collect(Collectors.toList());
    }

    public List<SplitStatistics> calculateSplitStatisticsForCategoricalFeature(int nclass) {
        if(nclass == 2) {
            // for binomial classification sort bins by the frequency of one class and split similarly to the sequential feature
            return calculateStatisticsForCategoricalFeatureBinomialClassification(nclass);
        } else {
            // full approach for binomial/multinomial/regression, works for up to 10 categories
             return calculateStatisticsForCategoricalFeatureFullApproach(nclass);
        }
    }

    private List<SplitStatistics> calculateStatisticsForCategoricalFeatureFullApproach(int nclass) {
        // calculate accumulative statistics for each subset of categories: 
        // left split - categories included in the subset; 
        // right split - categories not included in subset.
        assert _numOfCategories <= 10; // for numOfCategories >= 10 another implementation will be created 
        // as now the max supported category is 9 and for the bigger number the faster sequential approach should be used
        // init list with empty instances
        String categories = _bins.stream().map(b -> String.valueOf(((CategoricalBin) b)._category))
                .collect(Collectors.joining(""));
        Set<boolean[]> splits = findAllCategoricalSplits(categories);
        List<SplitStatistics> statistics = new ArrayList<>();
        for (boolean[] splitMask : splits) {
            SplitStatistics splitStatistics = new SplitStatistics(nclass);
            for (AbstractBin bin : _bins) {
                // if bin category is in the mask, it belongs to the left split, otherwise it belongs to the right split
                if (splitMask[((CategoricalBin) bin)._category]) {
                    splitStatistics.accumulateLeftStatistics(bin._count, bin._classesDistribution);
                } else {
                    splitStatistics.accumulateRightStatistics(bin._count, bin._classesDistribution);
                }
            }
            splitStatistics._splittingRule = new CategoricalSplittingRule(splitMask);
            statistics.add(splitStatistics);
        }
        return statistics;
    }

    private Set<boolean[]> findAllCategoricalSplits(String categories) {
        int recMaxDepth = categories.length() / 2; // floor. Generate only one half of the subsets as the rest is the complement
        Set<boolean[]> masks = new HashSet<>();
        // for 1 to recMaxDepth - 1 depth generate all options
        for (int depth = 1; depth < recMaxDepth; depth++) {
            for (String s : categories.split("")) {
                // use substring method to clone (deep copy) the string
                rec(masks, s, categories.substring(0).replaceAll(s, ""), depth - 1);
            }
        }
        // for recMaxDepth - 1 depth (the highest) generate only half of the options for the even length and all options for the odd
        if (categories.length() == recMaxDepth * 2) {
            // try only one first category
            rec(masks, categories.substring(0, 1), categories.substring(1), recMaxDepth - 1);
        } // nope: should be covered if generate only half of the depth and half of the combination (include first value / complementary will exclude it)
        else for (String s : categories.split("")) {
            // use substring method to clone (deep copy) the string
            rec(masks, s, categories.substring(0).replaceAll(s, ""), recMaxDepth - 1);
        }
        return masks;
    }

    private void rec(Set<boolean[]> masks, String current, String categories, int stepsToGo) {
        if (stepsToGo == 0 || categories.isEmpty()) {
            masks.add(createMaskFromString(current));
            return;
        }
        for (String s : categories.split("")) {
            if (s.charAt(0) > current.charAt(current.length() - 1))
                // use substring method to clone (deep copy) the string
                rec(masks, current + s, categories.substring(0).replaceAll(s, ""), stepsToGo - 1);
        }
    }

    private boolean[] createMaskFromString(String categories) {
        boolean[] mask = new boolean[_numOfCategories]; // array of false
        for (String c : categories.split("")) {
            mask[Integer.parseInt(c)] = true;
        }
        return mask;
    }

    private boolean[] createMaskFromBins(List<CategoricalBin> bins) {
        boolean[] mask = new boolean[_numOfCategories]; // array of false
        bins.stream().map(CategoricalBin::getCategory).forEach(c -> mask[c] = true);
        return mask;
    }

    public List<SplitStatistics> calculateStatisticsForCategoricalFeatureBinomialClassification(int nclass) {
        List<CategoricalBin> sortedBins = _bins.stream()
                .map(b -> (CategoricalBin) b)
                .sorted(Comparator.comparing(c -> c.getClassCount(0) / c._count))
                .collect(Collectors.toList());
        
        // init list with empty instances
        List<SplitStatistics> statistics = sortedBins.stream()
                .map(b -> new SplitStatistics(nclass)).collect(Collectors.toList());
        // calculate accumulative statistics for each split: 
        // left split - bins to the left + current; 
        // right split - bins to the right.
        SplitStatistics tmpAccumulatorLeft = new SplitStatistics(nclass);
        SplitStatistics tmpAccumulatorRight = new SplitStatistics(nclass);
        int rightIndex;
        for (int leftIndex = 0; leftIndex < statistics.size(); leftIndex++) {
            tmpAccumulatorLeft.accumulateLeftStatistics(sortedBins.get(leftIndex)._count, sortedBins.get(leftIndex)._classesDistribution);
            statistics.get(leftIndex).copyLeftValues(tmpAccumulatorLeft);
            statistics.get(leftIndex)._splittingRule = new CategoricalSplittingRule(
                    createMaskFromBins(sortedBins.subList(0, leftIndex + 1))); // subList takes toIndex exclusive, so +1
            // accumulate from the right (from the end of bins array)
            rightIndex = sortedBins.size() - leftIndex - 1;
            // firstly accumulate with old values, then add the actual bin for the future statistics 
            // as the values of the actual bin are not included in its right statistics
            statistics.get(rightIndex).copyRightValues(tmpAccumulatorRight);
            tmpAccumulatorRight.accumulateRightStatistics(sortedBins.get(rightIndex)._count, sortedBins.get(rightIndex)._classesDistribution);
        }
        return statistics;
    }
    
}
