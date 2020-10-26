package hex.tree.xgboost.matrix;

import hex.tree.xgboost.task.XGBoostUploadMatrixTask;
import ai.h2o.xgboost4j.java.util.BigDenseMatrix;
import water.Key;

import java.util.HashMap;
import java.util.Map;

public class RemoteMatrixLoader extends MatrixLoader {

    static abstract class RemoteMatrix {
        XGBoostUploadMatrixTask.MatrixData data;
        
        abstract MatrixLoader.DMatrixProvider make();
    }
    
    static class RemoteSparseMatrix extends RemoteMatrix {
        
        final SparseMatrixDimensions dims;
        final SparseMatrix matrix;

        RemoteSparseMatrix(SparseMatrixDimensions dims) {
            this.dims = dims;
            this.matrix = SparseMatrixFactory.allocateCSRMatrix(dims);
        }

        @Override
        MatrixLoader.DMatrixProvider make() {
            return SparseMatrixFactory.toDMatrix(matrix, dims, data.actualRows, data.shape, data.resp, data.weights, data.offsets);
        }
    }
    
    static class RemoteDenseMatrix extends RemoteMatrix {

        final XGBoostUploadMatrixTask.DenseMatrixDimensions dims;
        final BigDenseMatrix matrix;

        RemoteDenseMatrix(XGBoostUploadMatrixTask.DenseMatrixDimensions dims) {
            this.dims = dims;
            this.matrix = new BigDenseMatrix(dims.rows, dims.cols);
        }

        @Override
        DMatrixProvider make() {
            return new DenseMatrixFactory.DenseDMatrixProvider(data.actualRows, data.resp, data.weights, data.offsets, matrix);
        }
    }

    private static final Map<String, RemoteMatrix> REGISTRY = new HashMap<>();

    public static void initSparse(String key, SparseMatrixDimensions dims) {
        RemoteSparseMatrix m = new RemoteSparseMatrix(dims);
        REGISTRY.put(key, m);
    }

    public static void sparseChunk(String key, XGBoostUploadMatrixTask.SparseMatrixChunk chunk) {
        RemoteSparseMatrix m = (RemoteSparseMatrix) REGISTRY.get(key);
        long nonZeroCount = m.dims._precedingNonZeroElementsCounts[chunk.id];
        int rwRow = m.dims._precedingRowCounts[chunk.id];
        SparseMatrixFactory.NestedArrayPointer rowHeaderPointer = new SparseMatrixFactory.NestedArrayPointer(rwRow);
        SparseMatrixFactory.NestedArrayPointer dataPointer = new SparseMatrixFactory.NestedArrayPointer(nonZeroCount);
        for (int i = 0; i < chunk.rowHeader.length; i++) {
            rowHeaderPointer.setAndIncrement(m.matrix._rowHeaders, chunk.rowHeader[i]);
        }
        for (int i = 0; i < chunk.data.length; i++) {
            dataPointer.set(m.matrix._sparseData, chunk.data[i]);
            dataPointer.set(m.matrix._colIndices, chunk.colIndices[i]);
            dataPointer.increment();
        }
    }

    public static void initDense(String key, XGBoostUploadMatrixTask.DenseMatrixDimensions dims) {
        RemoteDenseMatrix m = new RemoteDenseMatrix(dims);
        REGISTRY.put(key, m);
    }

    public static void denseChunk(String key, XGBoostUploadMatrixTask.DenseMatrixChunk chunk) {
        RemoteDenseMatrix m = (RemoteDenseMatrix) REGISTRY.get(key);
        for (long i = 0; i < chunk.data.length; i++) {
            m.matrix.set(i + (m.dims.rowOffsets[chunk.id] * m.dims.cols), chunk.data[(int) i]);
        }
    }

    public static void matrixData(String key, XGBoostUploadMatrixTask.MatrixData data) {
        REGISTRY.get(key).data = data;
    }
    
    public static void cleanup(String key) {
        REGISTRY.remove(key);
    }

    private final Key modelKey;
    
    public RemoteMatrixLoader(Key modelKey) {
        this.modelKey = modelKey;
    }

    @Override
    public DMatrixProvider makeLocalMatrix() {
        return REGISTRY.remove(modelKey.toString()).make();
    }

}
