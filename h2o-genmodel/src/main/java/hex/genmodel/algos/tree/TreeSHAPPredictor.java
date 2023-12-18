package hex.genmodel.algos.tree;

import java.io.Serializable;

public interface TreeSHAPPredictor<R> extends Serializable  {

  float[] calculateContributions(final R feat, float[] out_contribs);

  float[] calculateContributions(final R feat,
                                 float[] out_contribs, int condition, int condition_feature,
                                 Workspace workspace);

  Workspace makeWorkspace();

  int getWorkspaceSize();

  interface Workspace {
    int getSize();
  }
  
}
