package hex.genmodel.algos.tree;

import java.util.Collection;

public class TreeSHAPEnsemble<R> implements TreeSHAPPredictor<R> {

  private final TreeSHAPPredictor<R>[] _predictors;
  private final float _initPred;
  private final int _wsMakerIndex;

  @SuppressWarnings("unchecked")
  public TreeSHAPEnsemble(Collection<TreeSHAPPredictor<R>> predictors, float initPred) {
    _predictors = predictors.toArray(new TreeSHAPPredictor[0]);
    _initPred = initPred;
    _wsMakerIndex = findWorkspaceMaker(_predictors);
  }

  @Override
  public float[] calculateContributions(R feat, float[] out_contribs) {
    return calculateContributions(feat, out_contribs, 0, -1, makeWorkspace());
  }

  @Override
  public float[] calculateContributions(R feat, float[] out_contribs, int condition, int condition_feature, TreeSHAP.Workspace workspace) {
    if (condition == 0) {
      out_contribs[out_contribs.length - 1] += _initPred;
    }
    for (TreeSHAPPredictor<R> predictor : _predictors) {
      predictor.calculateContributions(feat, out_contribs, condition, condition_feature, workspace);
    }
    return out_contribs; 
  }

  @Override
  public TreeSHAPPredictor.Workspace makeWorkspace() {
    return _wsMakerIndex >= 0 ? _predictors[_wsMakerIndex].makeWorkspace() : null;
  }

  @Override
  public int getWorkspaceSize() {
    return _wsMakerIndex >= 0 ? _predictors[_wsMakerIndex].getWorkspaceSize() : 0;
  }

  private static int findWorkspaceMaker(TreeSHAPPredictor<?>[] predictors) {
    if (predictors.length == 0)
      return -1;
    int maxSize = 0;
    int wsMakerIndex = 0;
    for (int i = 0; i < predictors.length; i++) {
      int size = predictors[i].getWorkspaceSize();
      if (size > maxSize) {
        maxSize = size;
        wsMakerIndex = i;
      }
    }
    return wsMakerIndex;
  }
  
}
