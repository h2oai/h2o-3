package hex.genmodel.algos.glrm;

import hex.ModelCategory;
import hex.genmodel.MojoModel;

import java.util.EnumSet;


/**
 */
public class GlrmMojoModel extends MojoModel {
  public int _ncolA;
  public int _ncolY;
  public int _nrowY;
  public double[][] _archetypes;
  public GlrmLoss[] _losses;
  public GlrmRegularizer _regx;
  public double _gammax;
  // We don't really care about regularization of Y since it is not used during scoring

  private static EnumSet<ModelCategory> CATEGORIES = EnumSet.of(ModelCategory.AutoEncoder, ModelCategory.DimReduction);
  @Override public EnumSet<ModelCategory> getModelCategories() {
    return CATEGORIES;
  }


  protected GlrmMojoModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    assert row.length == _ncolA;
    assert preds.length == _ncolY;
    return preds;
  }

}
