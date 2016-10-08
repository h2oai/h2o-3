package hex.genmodel.algos.glrm;

import hex.genmodel.MojoModel;


/**
 */
public class GlrmModel extends MojoModel {
  public int _ncolY;
  public int _nrowY;
  public double[][] _archetypes;
  public GlrmRegularizer _regx;
  public double _gammax;
  // We don't really care about regularization of Y since it is not used during scoring



  protected GlrmModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return preds;
  }

}
