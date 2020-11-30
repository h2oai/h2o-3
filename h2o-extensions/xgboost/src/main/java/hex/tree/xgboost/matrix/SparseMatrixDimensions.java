package hex.tree.xgboost.matrix;

import water.util.ArrayUtils;

import java.io.Serializable;

/**
 * Dimensions of a Sparse Matrix
 */
public final class SparseMatrixDimensions implements Serializable {

    public final int[] _precedingRowCounts;
    public final long[] _precedingNonZeroElementsCounts;

    public final long _nonZeroElementsCount;
    public final int _rowHeadersCount;

    /**
     * Constructs an instance of {@link SparseMatrixDimensions}
     *
     * @param nonZeroElementsCounts Number of non-zero elements (number of elements in sparse matrix). Also
     *                              number of column indices.
     * @param rowIndicesCounts      Number of indices of elements rows begin with
     */
    public SparseMatrixDimensions(int[] nonZeroElementsCounts, int[] rowIndicesCounts) {
        int precedingRowCount = 0;
        long precedingNonZeroCount = 0;
        _precedingRowCounts = new int[rowIndicesCounts.length];
        _precedingNonZeroElementsCounts = new long[nonZeroElementsCounts.length];
        assert rowIndicesCounts.length == nonZeroElementsCounts.length;
        for (int i = 0; i < nonZeroElementsCounts.length; i++) {
            _precedingRowCounts[i] = precedingRowCount;
            _precedingNonZeroElementsCounts[i] = precedingNonZeroCount;
            precedingRowCount += rowIndicesCounts[i];
            precedingNonZeroCount += nonZeroElementsCounts[i];
        }

        _nonZeroElementsCount = ArrayUtils.suml(nonZeroElementsCounts);
        _rowHeadersCount = ArrayUtils.sum(rowIndicesCounts) + 1;
    }
}
