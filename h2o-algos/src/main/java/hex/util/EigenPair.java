package hex.util;

public class EigenPair implements Comparable<EigenPair> {
  public double eigenvalue;
  public double[] eigenvector;

  public EigenPair(double eigenvalue, double[] eigenvector) {
    this.eigenvalue = eigenvalue;
    this.eigenvector = eigenvector;
  }

  /**
   * Compare an eigenPair = (eigenvalue, eigenVector) against otherEigenPair based on respective eigenValues
   */
  @Override
  public int compareTo(EigenPair otherEigenPair) {
    return eigenvalue < otherEigenPair.eigenvalue ? -1 : (eigenvalue > otherEigenPair.eigenvalue ? 1 : 0);
  }
}
