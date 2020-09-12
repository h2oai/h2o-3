package hex.tree.xgboost.matrix;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.Iced;

import java.io.*;

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
        public abstract void print();
        
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

    }
    
    public abstract DMatrixProvider makeLocalMatrix();
    
}
