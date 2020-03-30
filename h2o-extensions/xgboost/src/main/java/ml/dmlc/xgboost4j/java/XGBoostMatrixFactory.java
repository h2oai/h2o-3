package ml.dmlc.xgboost4j.java;

import water.Iced;

import java.io.IOException;

public abstract class XGBoostMatrixFactory extends Iced<XGBoostMatrixFactory> {

    public abstract DMatrix makeLocalMatrix() throws IOException, XGBoostError;

}
