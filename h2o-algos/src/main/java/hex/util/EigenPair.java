package hex.util;

/**
 * @author mathemage </ha@h2o.ai>
 * @date 18.5.17
 */
public class EigenPair implements Comparable<EigenPair> {
  public double eigenValue;
  public double[] eigenVectors;

  public EigenPair(double eigenValue, double[] eigenVectors) {
    this.eigenValue = eigenValue;
    this.eigenVectors = eigenVectors;
  }

  @Override
  public int compareTo(EigenPair otherEigenPair) {
    return eigenValue < otherEigenPair.eigenValue ? -1 : (eigenValue > otherEigenPair.eigenValue ? 1 : 0);
  }
}
