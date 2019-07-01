package hex.tree.xgboost.matrix;

/**
 * Sparse Matrix representation for XGBoost
 * 
 *  CSR:
 *     long[] rowHeaders = new long[] {0,      2,      4,         7}; // offsets
 *     float[] data = new float[]     {1f,2f,  4f,3f,  3f,1f,2f};     // non-zeros across each row
 *     int[] colIndex = new int[]     {0, 2,   0, 3,   0, 1, 2};      // col index for each non-zero
 */
public final class SparseMatrix {

    /**
     * Maximum size of one dimension of SPARSE matrix with data. Sparse matrix is square matrix.
     */
    public static int MAX_DIM = Integer.MAX_VALUE - 10;


    public final float[][] _sparseData;
    public final long[][] _rowHeaders;
    public final int[][] _colIndices;

    /**
     * Constructs a {@link SparseMatrix} instance
     *
     * @param sparseData Non-zero data of a sparse matrix
     * @param rowIndices Indices to elements in sparseData rows begin with
     * @param colIndices Column indices of elements in sparseData
     */
    public SparseMatrix(final float[][] sparseData, final long[][] rowIndices, final int[][] colIndices) {
        _sparseData = sparseData;
        _rowHeaders = rowIndices;
        _colIndices = colIndices;
    }
}
