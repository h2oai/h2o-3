package hex.genmodel.algos.tree;

import hex.genmodel.PredictContributions;

public abstract class ContributionsPredictor<E> implements PredictContributions {
  private final int _ncontribs;
  private final TreeSHAPPredictor<E> _treeSHAPPredictor;

  private static ThreadLocal<Object> _workspace = new ThreadLocal<>();

  public ContributionsPredictor(int ncontribs, TreeSHAPPredictor<E> treeSHAPPredictor) {
    _ncontribs = ncontribs;
    _treeSHAPPredictor = treeSHAPPredictor;
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

  private Object getWorkspace() {
    Object workspace = _workspace.get();
    if (workspace == null) {
      workspace = _treeSHAPPredictor.makeWorkspace();
      _workspace.set(workspace);
    }
    return workspace;
  }
}

