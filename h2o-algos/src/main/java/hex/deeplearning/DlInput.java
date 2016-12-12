package hex.deeplearning;

import org.apache.commons.collections.ListUtils;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.List;

/**
 * Stores DeepLearning input data (instead of KVM
 * Created by vpatryshev on 12/6/16.
 */
public class DlInput implements Serializable {
  public final DlColumn<Integer> target;
  public final DlColumn<Double>[] weights;
  
  public int target(int i) { return target.data.get(i); }
  
  public boolean isCategorical() {
    return target != null && target.size() > 0;
  } 

  @SuppressWarnings("unchecked")
  private DlColumn<Double>[] buildWeights(int size) {
    return (DlColumn<Double>[])Array.newInstance(DlColumn.class, size);
  }
  
  @SuppressWarnings("unchecked")
  public DlInput(List<Integer> target, long size, List<Double>... weightColumns) {
    this.target = new DlColumn<Integer>("target", target);
    this.weights = buildWeights(weightColumns.length);
    for (int i = 0; i < weightColumns.length; i++) {
      this.weights[i] = (new DlColumn<>("fv" + i, weightColumns[i]));
      i++;
    }
  }

  @SuppressWarnings("unchecked")
  public DlInput(List<Integer> target, long size, List<List<Double>> weightColumns) {
    
    this.target = new DlColumn<Integer>("target", target);
    this.weights = buildWeights(weightColumns.size());
    for (int i = 0; i < weightColumns.size(); i++) {
      this.weights[i] = (new DlColumn<>("fv" + i, weightColumns.get(i)));
      i++;
    }
  }

  @SuppressWarnings("unchecked")
  public DlInput(List<Integer> target, List<Double>... weightColumns) {
    assert weightColumns.length > 0;
    int size = weightColumns[0].size();
    this.target = new DlColumn<Integer>("target", target);
    this.weights = buildWeights(weightColumns.length);
    for (int i = 0; i < weightColumns.length; i++) {
      List<Double> weights = weightColumns[i];
      assert(weights.size() == size);
      this.weights[i] = (new DlColumn<>("fv" + i, weights));
      i++;
    }
  }

  @SuppressWarnings("unchecked")
  public DlInput(List<Integer> target, Double[]... weightColumns) {
    assert weightColumns.length > 0;
    int size = weightColumns[0].length;
    this.target = new DlColumn<Integer>("target", target);
    this.weights = buildWeights(weightColumns.length);
    for (int i = 0; i < weightColumns.length; i++) {
      Double[] weights = weightColumns[i];
      assert(weights.length == size);
      this.weights[i] = (new DlColumn<>("fv" + i, weights));
      i++;
    }
  }
}
