package hex.util;

import static java.util.Arrays.sort;

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

  public static EigenPair[] getSortedEigenpairs(double[] eigenvalues, double[][] eigenvectors) {
    int count = eigenvalues.length;
    EigenPair eigenPairs[] = new EigenPair[count];
    for (int i = 0; i < count; i++) {
      eigenPairs[i] = new EigenPair(eigenvalues[i], eigenvectors[i]);
    }
    sort(eigenPairs);
    return eigenPairs;
  }

  public static double[] extractEigenvalues(EigenPair[] eigenPairs) {
    int count = eigenPairs.length;
    double[] eigenvalues = new double[count];
    for (int i = 0; i < count; i++) {
      eigenvalues[i] = eigenPairs[i].eigenvalue;
    }
    return eigenvalues;
  }

  public static double[][] extractEigenvectors(EigenPair[] eigenPairs) {
    int count = eigenPairs.length;
    double[][] eigenvectors = new double[count][];
    for (int i = 0; i < count; i++) {
      eigenvectors[i] = eigenPairs[i].eigenvector;
    }
    return eigenvectors;
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
