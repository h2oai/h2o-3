package hex.createframe.columns;

import hex.createframe.CreateFrameColumnMaker;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Random;

/**
 * Random categorical column.
 */
public class CategoricalColumnCfcm extends CreateFrameColumnMaker {
  private String name;
  private int numFactors;
  private String[] domain;

  public CategoricalColumnCfcm() {
  }

  public CategoricalColumnCfcm(String colName, int nFactors) {
    name = colName;
    numFactors = nFactors;
    if (name.equals("response"))
      prepareAnimalDomain();
    else
      prepareSimpleDomain();
  }

  @Override public void exec(int nrows, NewChunk[] ncs, Random rng) {
    for (int row = 0; row < nrows; ++row)
      ncs[index].addNum((int)(rng.nextDouble() * numFactors));
  }

  @Override public String[] columnNames() {
    return new String[]{name};
  }

  @Override public byte[] columnTypes() {
    return new byte[]{Vec.T_CAT};
  }

  @Override public String[][] columnDomains() {
    return new String[][]{domain};
  }

  @Override public float byteSizePerRow() {
    return numFactors < 128 ? 1 : numFactors < 32768 ? 2 : 4;
  }


  private void prepareSimpleDomain() {
    domain = new String[numFactors];
    for (int i = 0; i < numFactors; ++i) {
      domain[i] = "c" + index + ".l" + i;
    }
  }

  private static String[] _animals =
      new String[]{"cat", "dog", "fish", "cow", "horse", "pig", "bird", "lion", "sheep", "rhino", "bull", "eagle",
                   "crab", "wolf", "duck", "crow", "fox", "bear", "hare", "camel", "bat", "frog", "ant", "otter",
                   "tiger", "rat", "snake", "zebra", "seal", "bison", "newt", "deer", "mouse", "turkey"};
  private void prepareAnimalDomain() {
    domain = new String[numFactors];
    System.arraycopy(_animals, 0, domain, 0, Math.min(numFactors, _animals.length));
    if (numFactors > _animals.length) {
      int k = _animals.length;
      OUTER:
      for (int i = 0; i < _animals.length; i++)
        for (int j = 0; j < _animals.length; j++) {
          if (i == j) continue;
          domain[k++] = _animals[i] + _animals[j];
          if (k == numFactors) break OUTER;
        }
    }
  }
}
