package hex.tree.xgboost.matrix;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.Iced;

import java.io.IOException;

public abstract class MatrixLoader extends Iced<MatrixLoader> {

    public abstract DMatrix makeLocalMatrix() throws IOException, XGBoostError;

}
