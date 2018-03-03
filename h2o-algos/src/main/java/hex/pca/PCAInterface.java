package hex.pca;

public interface PCAInterface {
  double[] getVariances();

  double[][] getPrincipalComponents();
}
