package hex.tree.sdt.binning;

import java.util.List;
import java.util.stream.Collectors;

public class FeatureBins {

    private List<Bin> _bins;
    private final boolean _isConstant;

    public FeatureBins(List<Bin> bins) {
        if (bins == null) {
            _isConstant = true;
        } else {
            _isConstant = false;
            _bins = bins;
        }
    }

//    public FeatureBins updateFeatureBins(List<Bin> bins) {
//        _bins = bins;
//        return this;
//    }

    public List<BinAccumulatedStatistics> calculateBinsStatisticsForFeature() {
        // init list with empty instances
        List<BinAccumulatedStatistics> statistics = _bins.stream()
                .map(b -> new BinAccumulatedStatistics()).collect(Collectors.toList());
        BinAccumulatedStatistics tmpAccumulatorLeft = new BinAccumulatedStatistics();
        BinAccumulatedStatistics tmpAccumulatorRight = new BinAccumulatedStatistics();
        int rightIndex;
        for (int leftIndex = 0; leftIndex < _bins.size(); leftIndex++) {
            tmpAccumulatorLeft.accumulateLeftStatistics(_bins.get(leftIndex)._count0, _bins.get(leftIndex)._count);
            statistics.get(leftIndex).copyLeftValues(tmpAccumulatorLeft);
            statistics.get(leftIndex)._maxBinValue = _bins.get(leftIndex)._max;
            // accumulate from the right (from the end of bins array)
            rightIndex = _bins.size() - leftIndex - 1;
            tmpAccumulatorRight.accumulateRightStatistics(_bins.get(rightIndex)._count0,
                    _bins.get(rightIndex)._count);
            statistics.get(rightIndex).copyRightValues(tmpAccumulatorRight);
        }
        return statistics;
    }

    public boolean isConstant() {
        return _isConstant;
    }
}
