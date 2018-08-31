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

  @Test
  public void testSetInputDouble() { // Deep Learning Version
    double[] row = {0, 7, 3, 42};
    int[] catOffsets = {1, 3, 8, 15};

    double[] nums = new double[1];
    int[] cats = new int[3];
    GenModel.setCats(row, nums, cats, 3, catOffsets, null, null, false);
    assertArrayEquals(new double[]{42}, nums, 0);
    assertArrayEquals(new int[]{-1, 7, 10}, cats);

    double[] to = new double[16];
    double[] numsInput = new double[1];
    int[] catsInput = new int[3];
    GenModel.setInput(row, to, numsInput, catsInput, numsInput.length, catsInput.length, catOffsets, null, null, false, false);
    assertArrayEquals(new double[]{0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 42}, to, 0);
  }

  @Test
  public void testSetInputFloat() { // DeepWater and XGBoost Native
    double[] row = {0, 7, 3, 42};
    int[] catOffsets = {1, 3, 8, 15};

    float[] to = new float[16];
    GenModel.setInput(row, to, 1, 3, catOffsets, null, null, false, false);
    assertArrayEquals(new float[]{0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 42}, to, 0);
  }

}