package hex.genmodel.easy.prediction;

/**
 * Class probability.
 *
 * Produced by method sortClassProbabilities() in class EasyPredictModelWrapper.
 */
public class SortedClassProbability implements Comparable {
  /**
   * Name of this class level.
   */
  public String name;

  /**
   * Prediction value for this class level.
   */
  public double probability;

  /**
   * Comparison implementation for this object type.
   *
   * @param o The other object to compare to.
   * @return -1, 0, 1 if this object is less than, equal, or greather than the other object.
   */
  @Override
  public int compareTo(Object o) {
    SortedClassProbability other = (SortedClassProbability) o;
    if (this.probability < other.probability) {
      return -1;
    }
    else if (this.probability > other.probability) {
      return 1;
    }
    else {
      return 0;
    }
  }
}
