package hex.genmodel.algos.glrm;

import hex.genmodel.MojoModel;


/**
 */
public class GlrmModel extends MojoModel {
  public GlrmRegularizer _regx;
  public double _gammax;

  protected GlrmModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return preds;
  }

}
