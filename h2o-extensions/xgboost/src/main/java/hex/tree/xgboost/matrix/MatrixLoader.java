package hex.tree.xgboost.matrix;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.Iced;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

public abstract class MatrixLoader extends Iced<MatrixLoader> {
    
    public static abstract class DMatrixProvider implements Serializable {

        protected static final byte SPARSE = 1;
        protected static final byte DENSE = 0;

        public static DMatrixProvider readFrom(DataInputStream is) throws IOException {
            int isSparse = is.read();
            if (isSparse == SPARSE) {
                return SparseMatrixFactory.SparseDMatrixProvider.readFrom(is);
            } else {
                return DenseMatrixFactory.DenseDMatrixProvider.readFrom(is);
            }
        }
        
        protected final long actualRows;
        protected final float[] response;
        protected final float[] weights;
        protected final float[] offsets;

        protected DMatrixProvider(long actualRows, float[] response, float[] weights, float[] offsets) {
            this.actualRows = actualRows;
            this.response = response;
            this.weights = weights;
            this.offsets = offsets;
        }

        protected abstract DMatrix makeDMatrix() throws XGBoostError;
        
        public DMatrix get() throws XGBoostError {
            DMatrix mat = makeDMatrix();
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

        public abstract void writeTo(DataOutputStream dos) throws IOException;

        protected void writeFloatArray(DataOutputStream dos, float[] a) throws IOException {
            if (a == null) {
                dos.writeInt(-1);
            } else {
                dos.writeInt(a.length);
                for (float v : a) dos.writeFloat(v);
            }
        }

        public static float[] readFloatArray(DataInputStream dis) throws IOException {
            int length = dis.readInt();
            if (length == -1) {
                return null;
            } else {
                float[] a = new float[length];
                for (int i = 0; i < a.length; i++) a[i] = dis.readFloat();
                return a;
            }
        }


    }
    
    public abstract DMatrixProvider makeLocalMatrix() throws IOException;
    
}
