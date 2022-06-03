package hex.tree.sdt.binning;

public class BinAccumulatedStatistics {
    public double _maxBinValue;

    public int _leftCount0;
    public int _leftCount;

    public int _rightCount0;
    public int _rightCount;

//    public BinAccumulatedStatistics(int count0, int count) {
//        _count0 = count0;
//        _count = count;
//    }
    
    public BinAccumulatedStatistics() {
        _leftCount0 = 0;
        _leftCount = 0;
        _rightCount0 = 0;
        _rightCount = 0;
    }

    public void accumulateLeftStatistics(int leftCount0, int leftCount) {
        _leftCount0 += leftCount0;
        _leftCount += leftCount;
    }

    public void accumulateRightStatistics(int rightCount0, int rightCount) {
        _rightCount0 += rightCount0;
        _rightCount += rightCount;
    }

    public void copyLeftValues(BinAccumulatedStatistics toCopy) {
        _leftCount0 = toCopy._leftCount0;
        _leftCount = toCopy._leftCount;
    }

    public void copyRightValues(BinAccumulatedStatistics toCopy) {
        _rightCount0 = toCopy._rightCount0;
        _rightCount = toCopy._rightCount;
    }
}
