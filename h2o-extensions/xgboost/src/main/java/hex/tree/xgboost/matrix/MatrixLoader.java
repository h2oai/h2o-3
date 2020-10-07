package hex.tree.xgboost.matrix;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.Iced;

import java.util.Arrays;
import java.util.Objects;

public abstract class MatrixLoader extends Iced<MatrixLoader> {
    
    public static abstract class DMatrixProvider {

        protected long actualRows;
        protected float[] response;
        protected float[] weights;
        protected float[] offsets;

        protected DMatrixProvider(long actualRows, float[] response, float[] weights, float[] offsets) {
            this.actualRows = actualRows;
            this.response = response;
            this.weights = weights;
            this.offsets = offsets;
        }
        
        protected abstract DMatrix makeDMatrix() throws XGBoostError;

        @SuppressWarnings("unused") // used for debugging
        public abstract void print(int nrow);
        
        protected void dispose() {}
        
        public final DMatrix get() throws XGBoostError {
            DMatrix mat = makeDMatrix();
            dispose();
            assert mat.rowNum() == actualRows;
            mat.setLabel(response);
            if (weights != null) {
                mat.setWeight(weights);
            }
            if (offsets != null) {
                mat.setBaseMargin(offsets);
            }
            return mat;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DMatrixProvider)) return false;
            DMatrixProvider that = (DMatrixProvider) o;
            return actualRows == that.actualRows &&
                Arrays.equals(response, that.response) &&
                Arrays.equals(weights, that.weights) &&
                Arrays.equals(offsets, that.offsets);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(actualRows);
            result = 31 * result + Arrays.hashCode(response);
            result = 31 * result + Arrays.hashCode(weights);
            result = 31 * result + Arrays.hashCode(offsets);
            return result;
        }
    }
    
    public abstract DMatrixProvider makeLocalMatrix();
    
}
