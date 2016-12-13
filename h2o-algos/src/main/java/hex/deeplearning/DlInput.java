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
  public final String name;
  
  public int target(int i) { return target.data.get(i); }
  
  public boolean isCategorical() {
    return target != null && target.size() > 0;
  } 

  @SuppressWarnings("unchecked")
  private DlColumn<Double>[] buildWeights(int size) {
    return (DlColumn<Double>[])Array.newInstance(DlColumn.class, size);
  }
  
  @SuppressWarnings("unchecked")
  public DlInput(String name, List<Integer> target, List<List<Double>> weightColumns) {
    this.name = name;
    this.target = new DlColumn<Integer>("target", target);
    this.weights = buildWeights(weightColumns.size());
    for (int i = 0; i < weightColumns.size(); i++) {
      this.weights[i] = new DlColumn<>("fv" + i, weightColumns.get(i));
    }
  }
  
  @Override public String toString() {
    return "DlInput(" + name + ") " + target.size() + " rows, " + weights.length + " columns";
  }

  void assertTrue(boolean value, String explanation) {
    if (!value) throw new IllegalStateException(explanation);
  }

  void assertTrue(boolean value) {
    assertTrue(value, "oops");
  }
  
  public void testMe(int expectedWidth) {
    assertTrue(weights != null);
    assertTrue(weights.length == expectedWidth);
      for (int i = 0; i < expectedWidth; i++) {
        final DlColumn<Double> column = weights[i];
        assertTrue(column != null, "@" + i);
      }
  }
}
