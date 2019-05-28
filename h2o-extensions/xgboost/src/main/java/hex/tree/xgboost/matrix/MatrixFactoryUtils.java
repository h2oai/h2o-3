package hex.tree.xgboost.matrix;

import water.fvec.Chunk;
import water.fvec.Vec;

public class MatrixFactoryUtils {

    public static int setResponseAndWeight(Chunk[] chunks, int respIdx, int weightIdx, float[] resp, float[] weights, int j, int i) {
        if (weightIdx != -1) {
            if(chunks[weightIdx].atd(i) == 0) {
                return j;
            }
            weights[j] = (float) chunks[weightIdx].atd(i);
        }
        resp[j++] = (float) chunks[respIdx].atd(i);
        return j;
    }

    public static int setResponseAndWeight(Vec.Reader w, float[] resp, float[] weights, Vec.Reader respVec, int j, long i) {
        if (w != null) {
            if(w.at(i) == 0) {
                return j;
            }
            weights[j] = (float) w.at(i);
        }
        resp[j++] = (float) respVec.at(i);
        return j;
    }

}
