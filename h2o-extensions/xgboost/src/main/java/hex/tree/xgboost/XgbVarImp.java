package hex.tree.xgboost;

import hex.VarImp;

public class XgbVarImp extends VarImp {

  final public float[] _covers; // Cover of each variable
  final public int[] _freqs; // Variable frequencies

  public XgbVarImp(String[] names, float[] gains, float[] covers, int[] freqs) {
    super(gains, names);
    _covers = covers;
    _freqs = freqs;
  }

}
