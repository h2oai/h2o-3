package hex.genmodel.algos.tree;

import java.util.Collection;

public class TreeSHAPEnsemble<R> implements TreeSHAPPredictor<R> {

  private final TreeSHAPPredictor<R>[] _predictors;
  private final float _initPred;

  @SuppressWarnings("unchecked")
  public TreeSHAPEnsemble(Collection<TreeSHAPPredictor<R>> predictors, float initPred) {
    _predictors = predictors.toArray(new TreeSHAPPredictor[0]);
    _initPred = initPred;
  }

  @Override
  public float[] calculateContributions(R feat, float[] out_contribs) {
    return calculateContributions(feat, out_contribs, 0, -1, makeWorkspace());
  }

  @Override
  public float[] calculateContributions(R feat, float[] out_contribs, int condition, int condition_feature, Object workspace) {
    Object[] workspaces = (Object[]) workspace;
    if (condition == 0) {
      out_contribs[out_contribs.length - 1] += _initPred;
    }
    for (int i = 0; i < _predictors.length; i++) {
      _predictors[i].calculateContributions(feat, out_contribs, condition, condition_feature, workspaces[i]);
    }
    return out_contribs; 
  }

  @Override
  public Object makeWorkspace() {
    Object[] workspaces = new Object[_predictors.length];
    for (int i = 0; i < workspaces.length; i++) {
      workspaces[i] = _predictors[i].makeWorkspace();
    }
    return workspaces;
  }

}
