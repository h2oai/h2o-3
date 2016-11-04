package hex.createframe;

import water.fvec.NewChunk;

import java.util.Random;

/**
 * This class describes parameters of a column that will be created by the CreateFrameExecutor
 */
public abstract class CreateFrameColumnSpec {
  protected int index;


  public void setIndex(int i) {
    index = i;
  }

  public float byteSizePerRow() {
    return 4;
  }

  public int workAmount() {
    return 100;
  }


  /**
   * Number of columns described by this column maker. Usually this is 1, however it is possible that some tasks
   * may create either 0 columns (i.e. they only modify existing ones), or create several columns at once (for example
   * if you're trying to create one-hot encoded categorical).
   */
  public int numColumns() {
    return 1;
  }

  public abstract byte[] columnTypes();

  public abstract String[] columnNames();

  public String[][] columnDomains() {
    return null;
  }

  public abstract void exec(int nrows, NewChunk[] cs, Random rng);

}
