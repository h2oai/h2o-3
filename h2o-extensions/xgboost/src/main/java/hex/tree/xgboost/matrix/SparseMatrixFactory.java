package hex.tree.xgboost.matrix;

import hex.DataInfo;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.H2O;
import water.LocalMR;
import water.MrFun;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import static hex.tree.xgboost.matrix.MatrixFactoryUtils.setResponseAndWeight;
import static water.MemoryManager.*;
import static water.MemoryManager.malloc4;

public class SparseMatrixFactory {

    public static DMatrix csr(
        Frame f, int[] chunksIds, Vec weightsVec, Vec responseVec, // for setupLocal
        DataInfo di, float[] resp, float[] weights)
        throws XGBoostError {

        SparseMatrixDimensions sparseMatrixDimensions = calculateCSRMatrixDimensions(f, chunksIds, weightsVec, di);
        SparseMatrix sparseMatrix = allocateCSRMatrix(sparseMatrixDimensions);

        Vec.Reader[] vecs = new Vec.Reader[f.numCols()];
        for (int i = 0; i < vecs.length; ++i) {
            vecs[i] = f.vec(i).new Reader();
        }
        Vec.Reader weightsReader = (weightsVec != null) ? weightsVec.new Reader() : null;
        Vec.Reader responseReader = responseVec.new Reader();

        int actualRows = initializeFromChunkIds(
            f, chunksIds, vecs, weightsReader,
            di, sparseMatrix._rowHeaders, sparseMatrix._sparseData, sparseMatrix._colIndices,
            responseReader, resp, weights);

        return toDMatrix(sparseMatrix, sparseMatrixDimensions, actualRows, di);
    }

    public static DMatrix csr(Chunk[] chunks, int weight, int respIdx, // for MR task
        DataInfo di, float[] resp, float[] weights) throws XGBoostError {

        SparseMatrixDimensions sparseMatrixDimensions = calculateCSRMatrixDimensions(chunks, di, weight);
        SparseMatrix sparseMatrix = allocateCSRMatrix(sparseMatrixDimensions);

        int actualRows = initializeFromChunks(
            chunks, weight,
            di, sparseMatrix._rowHeaders, sparseMatrix._sparseData, sparseMatrix._colIndices,
            respIdx, resp, weights);
        return toDMatrix(sparseMatrix, sparseMatrixDimensions, actualRows, di);
    }

    private static DMatrix toDMatrix(SparseMatrix sm, SparseMatrixDimensions smd, int actualRows, DataInfo di) throws XGBoostError {
        DMatrix trainMat = new DMatrix(sm._rowHeaders, sm._colIndices, sm._sparseData,
            DMatrix.SparseType.CSR, di.fullN(), actualRows + 1,
            smd._nonZeroElementsCount);
        assert trainMat.rowNum() == actualRows;
        return trainMat;
    }

    public static int initializeFromChunkIds(Frame f, int[] chunks, Vec.Reader[] vecs, Vec.Reader w, DataInfo di,
        long[][] rowHeaders, float[][] data, int[][] colIndex,
        Vec.Reader respVec, float[] resp, float[] weights) {

        // extract predictors
        int actualRows = 0;
        int nonZeroCount = 0;
        int rowPointer = 0;
        int currentCol = 0;
        int rwRow = 0;

        int rowHeaderRowPointer = 0;
        int rowHeaderColPointer = 0;

        int lastNonZeroRow = 0;

        for (Integer chunk : chunks) {
            for(long i = f.anyVec().espc()[chunk]; i < f.anyVec().espc()[chunk+1]; i++) {
                if (w != null && w.at(i) == 0) continue;
                actualRows++;
                if(rowHeaderColPointer == SparseMatrix.MAX_DIM){
                    rowHeaderColPointer = 0;
                    rowHeaderRowPointer++;
                }
                boolean foundNonZero = false;

                for (int j = 0; j < di._cats; ++j) {
                    if(currentCol == SparseMatrix.MAX_DIM){
                        currentCol = 0;
                        rowPointer++;
                    }
                    data[rowPointer][currentCol] = 1; //one-hot encoding

                    if(!foundNonZero){
                        foundNonZero = true;
                        for (int k = lastNonZeroRow; k < actualRows; k++) {
                            rowHeaders[rowHeaderRowPointer][rowHeaderColPointer++] = nonZeroCount;
                        }
                        lastNonZeroRow = actualRows;
                    }
                    if (vecs[j].isNA(i)) {
                        colIndex[rowPointer][currentCol++] = di.getCategoricalId(j, Float.NaN);
                    } else {
                        colIndex[rowPointer][currentCol++] = di.getCategoricalId(j, vecs[j].at8(i));
                    }
                    nonZeroCount++;
                }

                for (int j = 0; j < di._nums; ++j) {
                    if(currentCol == SparseMatrix.MAX_DIM){
                        currentCol = 0;
                        rowPointer++;
                    }
                    float val = (float) vecs[di._cats + j].at(i);
                    if (val != 0) {
                        data[rowPointer][currentCol] = val;
                        colIndex[rowPointer][currentCol++] = di._catOffsets[di._catOffsets.length - 1] + j;
                        if (!foundNonZero) {
                            foundNonZero = true;
                            for (int k = lastNonZeroRow; k < actualRows; k++) {
                                rowHeaders[rowHeaderRowPointer][rowHeaderColPointer++] = nonZeroCount;
                            }
                            lastNonZeroRow = actualRows;
                        }
                        nonZeroCount++;
                    }
                }

                rwRow = setResponseAndWeight(w, resp, weights, respVec, rwRow, i);
            }
        }
        for (int k = lastNonZeroRow; k <= actualRows; k++) {
            if(rowHeaderColPointer == SparseMatrix.MAX_DIM){
                rowHeaderColPointer = 0;
                rowHeaderRowPointer++;
            }
            rowHeaders[rowHeaderRowPointer][rowHeaderColPointer++] = nonZeroCount;
        }
        return actualRows;
    }

    private static int initializeFromChunks(Chunk[] chunks, int weight, DataInfo di, long[][] rowHeaders, float[][] data, int[][] colIndex, int respIdx, float[] resp, float[] weights) {
        int actualRows = 0;
        int nonZeroCount = 0;
        int rowPointer = 0;
        int currentCol = 0;
        int rwRow = 0;

        int rowHeaderRowPointer = 0;
        int rowHeaderColPointer = 0;
        int lastNonZeroRow = 0;

        for (int i = 0; i < chunks[0].len(); i++) {
            if (weight != -1 && chunks[weight].atd(i) == 0) continue;
            actualRows++;
            if(rowHeaderColPointer == SparseMatrix.MAX_DIM){
                rowHeaderColPointer = 0;
                rowHeaderRowPointer++;
            }
            boolean foundNonZero = false;

            for (int j = 0; j < di._cats; ++j) {
                if(currentCol == SparseMatrix.MAX_DIM){
                    currentCol = 0;
                    rowPointer++;
                }

                data[rowPointer][currentCol] = 1; //one-hot encoding
                if(!foundNonZero){
                    foundNonZero = true;
                    for (int k = lastNonZeroRow; k < actualRows; k++) {
                        rowHeaders[rowHeaderRowPointer][rowHeaderColPointer++] = nonZeroCount;
                    }
                    lastNonZeroRow = actualRows;
                }
                if (chunks[j].isNA(i)) {
                    colIndex[rowPointer][currentCol++] = di.getCategoricalId(j, Float.NaN);
                } else {
                    colIndex[rowPointer][currentCol++] = di.getCategoricalId(j, chunks[j].at8(i));
                }
                nonZeroCount++;
            }
            for (int j = 0; j < di._nums; ++j) {
                if(currentCol == SparseMatrix.MAX_DIM){
                    currentCol = 0;
                    rowPointer++;
                }
                float val = (float) chunks[di._cats + j].atd(i);
                if (val != 0) {
                    data[rowPointer][currentCol] = val;
                    colIndex[rowPointer][currentCol++] = di._catOffsets[di._catOffsets.length - 1] + j;
                    if(!foundNonZero){
                        foundNonZero = true;
                        for (int k = lastNonZeroRow; k < actualRows; k++) {
                            rowHeaders[rowHeaderRowPointer][rowHeaderColPointer++] = nonZeroCount;
                        }
                        lastNonZeroRow = actualRows;
                    }
                    nonZeroCount++;
                }
            }

            rwRow = setResponseAndWeight(chunks, respIdx, weight, resp, weights, rwRow, i);
        }
        for (int k = lastNonZeroRow; k <= actualRows; k++) {
            if(rowHeaderColPointer == SparseMatrix.MAX_DIM){
                rowHeaderColPointer = 0;
                rowHeaderRowPointer++;
            }
            rowHeaders[rowHeaderRowPointer][rowHeaderColPointer++] = nonZeroCount;
        }
        return actualRows;
    }

    /**
     * Creates a {@link SparseMatrix} object with pre-instantiated backing arrays for row-oriented compression schema (CSR).
     * All backing arrays are allocated using MemoryManager.
     *
     * @param sparseMatrixDimensions Dimensions of a sparse matrix
     * @return An instance of {@link SparseMatrix} with pre-allocated backing arrays.
     */
    public static SparseMatrix allocateCSRMatrix(SparseMatrixDimensions sparseMatrixDimensions) {
        // Number of rows in non-zero elements matrix
        final int dataRowsNumber = (int) (sparseMatrixDimensions._nonZeroElementsCount / SparseMatrix.MAX_DIM);
        final int dataLastRowSize = (int)(sparseMatrixDimensions._nonZeroElementsCount % SparseMatrix.MAX_DIM);
        //Number of rows in matrix with row indices
        final int rowIndicesRowsNumber = (int)(sparseMatrixDimensions._rowHeadersCount / SparseMatrix.MAX_DIM);
        final int rowIndicesLastRowSize = (int)(sparseMatrixDimensions._rowHeadersCount % SparseMatrix.MAX_DIM);
        // Number of rows in matrix with column indices of sparse matrix non-zero elements
        // There is one column index per each non-zero element, no need to recalculate.
        final int colIndicesRowsNumber = dataRowsNumber;
        final int colIndicesLastRowSize = dataLastRowSize;

        // Sparse matrix elements (non-zero elements)
        float[][] sparseData = new float[dataLastRowSize == 0 ? dataRowsNumber : dataRowsNumber + 1][];
        int iterationLimit = dataLastRowSize == 0 ? sparseData.length : sparseData.length - 1;
        for (int sparseDataRow = 0; sparseDataRow < iterationLimit; sparseDataRow++) {
            sparseData[sparseDataRow] = malloc4f(SparseMatrix.MAX_DIM);
        }
        if (dataLastRowSize > 0) {
            sparseData[sparseData.length - 1] = malloc4f(dataLastRowSize);
        }
        // Row indices
        long[][] rowIndices = new long[rowIndicesLastRowSize == 0 ? rowIndicesRowsNumber : rowIndicesRowsNumber + 1][];
        iterationLimit = rowIndicesLastRowSize == 0 ? rowIndices.length : rowIndices.length - 1;
        for (int rowIndicesRow = 0; rowIndicesRow < iterationLimit; rowIndicesRow++) {
            rowIndices[rowIndicesRow] = malloc8(SparseMatrix.MAX_DIM);
        }
        if (rowIndicesLastRowSize > 0) {
            rowIndices[rowIndices.length - 1] = malloc8(rowIndicesLastRowSize);
        }

        // Column indices
        int[][] colIndices = new int[colIndicesLastRowSize == 0 ? colIndicesRowsNumber : colIndicesRowsNumber + 1][];
        iterationLimit = colIndicesLastRowSize == 0 ? colIndices.length : colIndices.length - 1;
        for (int colIndicesRow = 0; colIndicesRow < iterationLimit; colIndicesRow++) {
            colIndices[colIndicesRow] = malloc4(SparseMatrix.MAX_DIM);
        }
        if (colIndicesLastRowSize > 0) {
            colIndices[colIndices.length - 1] = malloc4(colIndicesLastRowSize);
        }

        // Wrap backing arrays into a SparseMatrix object and return them
        return new SparseMatrix(sparseData, rowIndices, colIndices);
    }

    protected static SparseMatrixDimensions calculateCSRMatrixDimensions(Chunk[] chunks, DataInfo di, int weightColIndex){

        long[] nonZeroElementsCounts = new long[1];
        long[] rowIndicesCounts = new long[1];

        for (int i = 0; i < chunks[0].len(); i++) {
            // Rows with zero weights are going to be ignored
            if (weightColIndex != -1 && chunks[weightColIndex].atd(i) == 0) continue;
            rowIndicesCounts[0]++;

            nonZeroElementsCounts[0] += di._cats;

            for (int j = 0; j < di._nums; ++j) {
                double val = chunks[di._cats + j].atd(i);
                if (val != 0) {
                    nonZeroElementsCounts[0]++;
                }
            }
        }

        return new SparseMatrixDimensions(nonZeroElementsCounts, rowIndicesCounts);
    }

    public static SparseMatrixDimensions calculateCSRMatrixDimensions(Frame f, int[] chunkIds, Vec w, DataInfo di) {
        CalculateCSRMatrixDimensionsMrFun fun = new CalculateCSRMatrixDimensionsMrFun(f, di, w, chunkIds);
        H2O.submitTask(new LocalMR(fun, chunkIds.length)).join();

        return new SparseMatrixDimensions(fun._nonZeroElementsCounts, fun._rowIndicesCounts);
    }

    private static class CalculateCSRMatrixDimensionsMrFun extends MrFun<CalculateCSRMatrixDimensionsMrFun> {
        private Frame _f;
        private DataInfo _di;
        private Vec _w;
        private int[] _chunkIds;

        // OUT
        private long[] _rowIndicesCounts;
        private long[] _nonZeroElementsCounts;

        CalculateCSRMatrixDimensionsMrFun(Frame f, DataInfo di, Vec w, int[] chunkIds) {
            _f = f;
            _di = di;
            _w = w;
            _chunkIds = chunkIds;
            _rowIndicesCounts = new long[chunkIds.length];
            _nonZeroElementsCounts = new long[chunkIds.length];
        }

        @Override
        protected void map(int i) {
            final int cidx = _chunkIds[i];

            long rowIndicesCount = 0;
            long nonZeroElementsCount = 0;

            if (_di._nums == 0) {
                if (_w == null) {
                    // no weights and only categoricals => sizing is trivial
                    rowIndicesCount = _f.anyVec().chunkForChunkIdx(cidx)._len;
                    nonZeroElementsCount = rowIndicesCount * _di._cats;
                } else {
                    Chunk ws = _w.chunkForChunkIdx(cidx);
                    int nzWeights = 0;
                    for (int r = 0; r < ws._len; r++)
                        if (ws.atd(r) != 0) {
                            nzWeights++;
                        }
                    rowIndicesCount += nzWeights;
                    nonZeroElementsCount += nzWeights * _di._cats;
                }
            } else {
                Chunk[] cs = new Chunk[_di._nums];
                for (int c = 0; c < cs.length; c++) {
                    cs[c] = _f.vec(_di._cats + c).chunkForChunkIdx(cidx);
                }
                Chunk ws = _w != null ? _w.chunkForChunkIdx(cidx) : null;
                for (int r = 0; r < cs[0]._len; r++) {
                    if (ws != null && ws.atd(r) == 0)
                        continue;
                    rowIndicesCount++;
                    nonZeroElementsCount += _di._cats;
                    for (int j = 0; j < _di._nums; j++) {
                        if (cs[j].atd(r) != 0) {
                            nonZeroElementsCount++;
                        }
                    }
                }
            }
            _rowIndicesCounts[i] = rowIndicesCount;
            _nonZeroElementsCounts[i] = nonZeroElementsCount;
        }
    }

}
