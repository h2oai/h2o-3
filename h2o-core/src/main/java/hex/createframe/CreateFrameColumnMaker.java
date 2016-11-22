package hex.createframe;

import water.Iced;
import water.fvec.NewChunk;

import java.util.Random;

/**
 * Base class for all "column makers" used by the CreateFrameExecutor to construct the frame.
 * Typically a subclass would be creating just a single column having a certain type, name
 * and the distribution of values. However it is also possible to create a subclass that
 * constructs 0 columns (i.e. just modifies the previously constructed ones), or one that
 * creates more than 1 columns at once.
 */
public abstract class CreateFrameColumnMaker extends Iced<CreateFrameColumnMaker> {
  protected int index;

  /**
   * Implement this method in a subclass to actually build the columns.
   *
   * @param nrows Number of rows in the current chunk. If method is creating new columns,
   *              then it is supposed to add this many rows.
   * @param ncs The `NewChunk`s array passed down from the `map()` method in `MRTask`.
   *            A subclass is expected to know which NewChunks it is allowed to touch,
   *            usually with the help of the {@link #index} variable.
   * @param rng Random number generator that the subclass may use to fill the columns
   *            randomly. Do NOT use any other random generator as doing so will break
   *            the reproducibility promise of the CreateFrame service.
   */
  public abstract void exec(int nrows, NewChunk[] ncs, Random rng);

  /**
   * Number of columns described by this column maker. Usually this is 1, however it is possible that some tasks
   * may create either 0 columns (i.e. they only modify existing ones), or create several columns at once (for example
   * if you're trying to create one-hot encoded categorical).
   */
  public int numColumns() {
    return 1;
  }

  /**
   * Types of the columns produced by the column maker. The returned array should have
   * exactly the same number of elements as given by {@link #numColumns()}.
   */
  public abstract byte[] columnTypes();

  /**
   * Names of the columns produces by this column maker. Should also have the same
   * number of elements as given by {@link #numColumns()}.
   */
  public abstract String[] columnNames();

  /**
   * Domains for categorical columns being created (if any).
   */
  public String[][] columnDomains() {
    return null;
  }


  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Index of the first column that this column maker will be creating. This
   * method is used by the executor, and the {@link #index} variable it sets
   * can be used to determine which columns in the <code>ncs</code> array to
   * fill during the {@link #exec(int, NewChunk[], Random)} step.
   */
  public void setIndex(int i) {
    index = i;
  }

  /**
   * Estimated byte size of a single row created by this column maker. This
   * estimate is later used to determine optimal chunk size for the produced
   * frame, thus it doesn't have to be very precise.
   */
  public float byteSizePerRow() {
    return 4;
  }

  /**
   * <p>Relative amount of work this column maker performs to fill a chunk. The
   * base amount of 100 corresponds to a method that draws a single random
   * number per row and then uses simple arithmetic before adding a value to
   * the NewChunk.
   * <p>The output will be used to inform te {@link water.Job} about progress
   * being made. It needn't be very precise.
   */
  public int workAmount() {
    return 100;
  }

}
