package hex.genmodel.algos.pca;

import hex.genmodel.MojoModel;

public class PCAMojoModel extends MojoModel {

  double[][] _eigenvectors_raw;
  public int [] _catOffsets;
  public int[] _permutation;
  public int _ncats;
  public int _nnums;
  public double[] _normSub; // used to perform dataset transformation.  When no transform is needed, will be 0
  public double[] _normMul; // used to perform dataset transformation.  When no transform is needed, will be 1
  public boolean _use_all_factor_levels;
  public String _pca_method;
  public String _pca_impl;
  public int _k;
  public int _eigenVectorSize;
  
  public PCAMojoModel(String[] columns, String[][] domains, String responseColumn)  {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    assert(row!=null):"input data row is null";
    double[] tpred = preds==null?new double[_k]:preds;  // allocate tpred in case it is null
    int numStart = _catOffsets[_ncats];
    assert(row.length == _nnums + _ncats):"assert dataset input size does not eqaul to expected size";

    for(int i = 0; i < _k; i++) {
      tpred[i] = 0;
      for (int j = 0; j < _ncats; j++) {
        double tmp = row[_permutation[j]];
        if (Double.isNaN(tmp)) continue;    // Missing categorical values are skipped
        int last_cat = _catOffsets[j+1]-_catOffsets[j]-1;
        int level = (int)tmp - (_use_all_factor_levels ? 0:1);  // Reduce index by 1 if first factor level dropped during training
        if (level < 0 || level > last_cat) continue;  // Skip categorical level in test set but not in train
        tpred[i] += _eigenvectors_raw[_catOffsets[j]+level][i];
      }

      int dcol = _ncats;
      int vcol = numStart;
      for (int j = 0; j < _nnums; j++) {
        tpred[i] += (row[_permutation[dcol]] - _normSub[j]) * _normMul[j] * _eigenvectors_raw[vcol][i];
        dcol++; vcol++;
      }
    }   
    return tpred;
  }

  @Override public int getPredsSize() {
    return _k;
  }

  @Override public int nclasses() {
    return _k;
  }

  @Override
  public String[] getOutputNames() {
    String[] names = new String[_k];
    for (int i = 0; i < names.length; i++) {
      names[i] = "PC" + (i + 1);
    }
    return names;
  }

}
