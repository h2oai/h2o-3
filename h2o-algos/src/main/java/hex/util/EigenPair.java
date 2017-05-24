package hex.util;

/**
 * @author mathemage <ha@h2o.ai>
 * created on 18.5.17
 */
public class EigenPair implements Comparable<EigenPair> {
  public double eigenvalue;
  public double[] eigenvector;

  public EigenPair(double eigenvalue, double[] eigenvector) {
    this.eigenvalue = eigenvalue;
    this.eigenvector = eigenvector;
  }

  /**
   * @author mathemage <ha@h2o.ai>
   * Compare an eigenPair = (eigenvalue, eigenVector) against otherEigenPair based on respective eigenValues
   */
  @Override
  public int compareTo(EigenPair otherEigenPair) {
    return eigenvalue < otherEigenPair.eigenvalue ? -1 : (eigenvalue > otherEigenPair.eigenvalue ? 1 : 0);
  }
}
