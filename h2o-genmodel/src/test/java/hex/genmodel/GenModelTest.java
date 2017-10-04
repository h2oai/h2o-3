package hex.genmodel;

import org.junit.Test;

import static org.junit.Assert.*;

public class GenModelTest {

  @Test
  public void testKMeansDistance() throws Exception {
    double[] center = new double[]{1.2, 0.8, 1.0};
    double[] point = new double[]{1.0, Double.NaN, 1.8};
    double dist = GenModel.KMeans_distance(center, point, new String[center.length][]);
    assertEquals(3.0*((0.2*0.2)+(0.8*0.8))/2.0, dist, 1e-10);
  }

  @Test
  public void testKMeansDistanceExtended() throws Exception {
    double[] center = new double[]{1.2, 0.8, 1.0};
    float[] point = new float[]{1.0f, Float.NaN, 1.8f};
    double dist = GenModel.KMeans_distance(center, point, new int[]{-1,-1,-1}, new double[3], new double[3]);
    assertEquals(3.0*(((1.2-1.0f)*(1.2-1.0f))+((1.0-1.8f)*(1.0-1.8f)))/2.0f, dist, 1e-10);
  }

}