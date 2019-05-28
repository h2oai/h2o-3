package hex.tree.xgboost.matrix;

import water.util.ArrayUtils;

/**
 * Dimensions of a Sparse Matrix
 */
public final class SparseMatrixDimensions {

    public final long[] _rowHeadersCounts;
    public final long[] _nonZeroElementsCounts;

    public final long _nonZeroElementsCount;
    public final long _rowHeadersCount;

    /**
     * Constructs an instance of {@link SparseMatrixDimensions}
     *
     * @param nonZeroElementsCounts Number of non-zero elements (number of elements in sparse matrix). Also
     *                              number of column indices.
     * @param rowIndicesCounts      Number of indices of elements rows begin with
     */
    public SparseMatrixDimensions(long[] nonZeroElementsCounts, long[] rowIndicesCounts) {
        _nonZeroElementsCounts = nonZeroElementsCounts;
        _rowHeadersCounts = rowIndicesCounts;

        _nonZeroElementsCount = ArrayUtils.sum(_nonZeroElementsCounts);
        _rowHeadersCount = ArrayUtils.sum(_rowHeadersCounts) + 1;
    }
}
