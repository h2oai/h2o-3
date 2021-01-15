package hex.genmodel.algos.tree;

import hex.genmodel.PredictContributions;
import hex.genmodel.utils.ArrayUtils;

import hex.genmodel.attributes.parameters.KeyValue;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.utils.ArrayUtils;
import java.util.Arrays;
import java.util.Comparator;

public abstract class ContributionsPredictor<E> implements PredictContributions {
  private final int _ncontribs;
  private final String[] _contribution_names;
  private final TreeSHAPPredictor<E> _treeSHAPPredictor;

  private static final ThreadLocal<Object> _workspace = new ThreadLocal<>();

  public ContributionsPredictor(int ncontribs, String[] featureContributionNames, TreeSHAPPredictor<E> treeSHAPPredictor) {
    _ncontribs = ncontribs;
    _contribution_names = ArrayUtils.append(featureContributionNames, "BiasTerm");
    _treeSHAPPredictor = treeSHAPPredictor;
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

  /**
   * Calculate and sort shapley values.
   *
   * @param input A new data point.
   * @param topN Return only #topN highest contributions + bias.
   * @param topBottomN Return only #topBottomN lowest contributions + bias
   *                   If topN and topBottomN are defined together then return array of #topN + #topBottomN + bias
   * @param abs True to compare absolute values of contributions
   * @return Sorted KeyValue array of contributions of size #topN + #topBottomN + bias
   *         If topN < 0 || topBottomN < 0 then all descending sorted contributions is returned.
   */
  public final KeyValue[] calculateContributions(double[] input, int topN, int topBottomN, boolean abs) {
    float[] contribs = calculateContributions(input);
    if (topBottomN == 0) {
      return composeSortedContributions(contribs, topN, new KeyValue.DescComparator(abs));
    } else if (topN == 0){
      return composeSortedContributions(contribs, topBottomN, new KeyValue.AscComparator(abs));
    } else if ((topN + topBottomN) >= _ncontribs || topN < 0 || topBottomN < 0) {
      return composeSortedContributions(contribs, _ncontribs, new KeyValue.DescComparator(abs));
    }
    
    KeyValue[] topSorted = composeSortedContributions(contribs, topN, new KeyValue.DescComparator(abs));
    topSorted = Arrays.copyOf(topSorted, topSorted.length - 1);
    KeyValue[] bottomSorted = composeSortedContributions(contribs, topBottomN, new KeyValue.AscComparator(abs));
    
    return ArrayUtils.appendGeneric(topSorted, bottomSorted);
  }

  private KeyValue[] composeSortedContributions(float[] contribs, int n, Comparator<? super KeyValue> comparator) {
    if (n <= 0 || n > _ncontribs) {
      n = _ncontribs;
    }
    KeyValue[] sortedContributions = sortContributions(contribs, comparator);
    if (n < _ncontribs) {
      KeyValue bias = sortedContributions[_ncontribs-1];
      sortedContributions = Arrays.copyOfRange(sortedContributions, 0, n + 1);
      sortedContributions[n] = bias;
    }
    return sortedContributions;
  }

  private KeyValue[] sortContributions(float[] contribs, Comparator<? super KeyValue> comparator) {
    KeyValue[] sorted = new KeyValue[_ncontribs];
    for (int i = 0; i < _ncontribs; i++) {
      sorted[i] = new KeyValue(_contribution_names[i], contribs[i]);
    }
    Arrays.sort(sorted, 0, _ncontribs -1 /*exclude bias*/, comparator);
    return sorted;
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

