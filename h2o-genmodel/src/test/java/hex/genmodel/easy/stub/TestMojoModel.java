package hex.genmodel.easy.stub;

import hex.ModelCategory;
import hex.genmodel.MojoModel;

public class TestMojoModel extends MojoModel {
  @Override
  public int nfeatures() {
    return 3;
  }

  @Override
  public ModelCategory getModelCategory() { return null; }
  @Override
  public String getUUID() { return null; }

  private static final String[][] DOMAINS = new String[][]{
          new String[]{"S", "Q"},
          null, //age not a categorical feature
          new String[]{"male", "female"}
  };

  public TestMojoModel() {
    super(new String[]{ "embarked", "age", "sex" }, DOMAINS, null);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return new double[0];
  }
}
