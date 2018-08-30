package hex.genmodel.algos.xgboost;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;

import java.io.Closeable;


/**
 * "Gradient Boosting Machine" MojoModel
 */
public abstract class XGBoostMojoModel extends MojoModel implements Closeable {

  public enum ObjectiveType {
    BINARY_LOGISTIC("binary:logistic"),
    REG_GAMMA("reg:gamma"),
    REG_TWEEDIE("reg:tweedie"),
    COUNT_POISSON("count:poisson"),
    REG_LINEAR("reg:linear"),
    MULTI_SOFTPROB("multi:softprob");

    private String _id;

    ObjectiveType(String id) {
      _id = id;
    }

    public String getId() {
      return _id;
    }

    public static ObjectiveType fromXGBoost(String type) {
      for (ObjectiveType t : ObjectiveType.values())
        if (t.getId().equals(type))
          return t;
      return null;
    }
  }

  public int _nums;
  public int _cats;
  public int[] _catOffsets;
  public boolean _useAllFactorLevels;
  public boolean _sparse;
  public String _featureMap;

  public XGBoostMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  // finalize MOJO initialization after all the fields are read
  public void postReadInit() {}

  @Override
  public final double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  // for float output
  public static double[] toPreds(double in[], float[] out, double[] preds,
                          int nclasses, double[] priorClassDistrib, double defaultThreshold) {
    if (nclasses > 2) {
      for (int i = 0; i < out.length; ++i)
        preds[1 + i] = out[i];
      preds[0] = GenModel.getPrediction(preds, priorClassDistrib, in, defaultThreshold);
    } else if (nclasses==2){
      preds[1] = 1 - out[0];
      preds[2] = out[0];
      preds[0] = GenModel.getPrediction(preds, priorClassDistrib, in, defaultThreshold);
    } else {
      preds[0] = out[0];
    }
    return preds;
  }

}
