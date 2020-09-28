package hex.tree.xgboost.matrix;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.Iced;

import java.io.*;

public abstract class MatrixLoader extends Iced<MatrixLoader> {
    
    public static abstract class DMatrixProvider implements Externalizable {

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
        
        public DMatrixProvider() {}

        protected abstract DMatrix makeDMatrix() throws XGBoostError;
        
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
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeLong(actualRows);
            out.writeObject(response);
            out.writeObject(weights);
            out.writeObject(offsets);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            actualRows = in.readLong();
            response = (float[]) in.readObject();
            weights = (float[]) in.readObject();
            offsets = (float[]) in.readObject();
        }
    }
    
    public abstract DMatrixProvider makeLocalMatrix();
    
}
