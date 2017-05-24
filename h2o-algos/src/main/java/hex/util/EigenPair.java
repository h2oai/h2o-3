package hex.util;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.sort;

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

  public static List<EigenPair> getSortedEigenpairs(int count, double[] eigenvalues, double[][] eigenvectors) {
    List<EigenPair> eigenPairs = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      eigenPairs.add(new EigenPair(eigenvalues[i], eigenvectors[i]));
    }
    sort(eigenPairs);
    return eigenPairs;
  }

  public static double[] extractEigenvalues(List<EigenPair> eigenPairs) {
    int count = eigenPairs.size();
    double[] eigenvalues = new double[count];
    for (int i = 0; i < count; i++) {
      eigenvalues[i] = eigenPairs.get(i).eigenvalue;
    }
    return eigenvalues;
  }

  public static double[][] extractEigenvectors(List<EigenPair> eigenPairs) {
    int count = eigenPairs.size();
    double[][] eigenvectors = new double[count][];
    for (int i = 0; i < count; i++) {
      eigenvectors[i] = eigenPairs.get(i).eigenvector;
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
