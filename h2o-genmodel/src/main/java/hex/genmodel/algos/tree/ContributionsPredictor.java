package hex.genmodel.algos.tree;

import hex.genmodel.PredictContributions;
import hex.genmodel.utils.ArrayUtils;

public abstract class ContributionsPredictor<E> implements PredictContributions {
  private final int _ncontribs;
  private final String[] _contribution_names;
  private final TreeSHAPPredictor<E> _treeSHAPPredictor;
  private final int _workspaceSize;

  private static final ThreadLocal<TreeSHAPPredictor.Workspace> _workspace = new ThreadLocal<>();

  public ContributionsPredictor(int ncontribs, String[] featureContributionNames, TreeSHAPPredictor<E> treeSHAPPredictor) {
    _ncontribs = ncontribs;
    _contribution_names = ArrayUtils.append(featureContributionNames, "BiasTerm");
    _treeSHAPPredictor = treeSHAPPredictor;
    _workspaceSize = _treeSHAPPredictor.getWorkspaceSize();
  }

  @Override
  public final String[] getContributionNames() {
    return _contribution_names;
  }

  public final float[] calculateContributions(double[] input) {
    float[] contribs = new float[_ncontribs];
    _treeSHAPPredictor.calculateContributions(toInputRow(input), contribs, 0, -1, getWorkspace());
    return getContribs(contribs);
  }

  protected abstract E toInputRow(double[] input);

  public float[] getContribs(float[] contribs) {
    return contribs;
  }

  private TreeSHAPPredictor.Workspace getWorkspace() {
    TreeSHAPPredictor.Workspace workspace = _workspace.get();
    if (workspace == null || workspace.getSize() != _workspaceSize) {
      workspace = _treeSHAPPredictor.makeWorkspace();
      assert workspace.getSize() == _workspaceSize;
      _workspace.set(workspace);
    }
    return workspace;
  }
}

