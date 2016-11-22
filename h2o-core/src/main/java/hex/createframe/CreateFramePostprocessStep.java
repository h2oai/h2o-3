package hex.createframe;

import water.Iced;
import water.fvec.Frame;

import java.util.Random;

/**
 * <p>Base class for any "postprocessing" steps that should be undertaken after
 * the frame has been created using {@link CreateFrameColumnMaker}s.</p>
 *
 * <p>Each postprocess step takes a frame as an input, and then modifies it
 * in-place. Examples of such postprocessing tasks could be: column renaming /
 * reordering; removal of some temporary columns; etc.</p>
 */
public abstract class CreateFramePostprocessStep extends Iced<CreateFramePostprocessStep> {

  /**
   * This method performs the actual work of the postprocessing task.
   *
   * @param fr  Frame that the task modifies.
   * @param rng Random number generator to use if the task needs to modify the
   *            frame randomly.
   */
  public abstract void exec(Frame fr, Random rng);

  /**
   * Approximate work amount for this step. The default value of 100 is the
   * same as each column maker's amount of work per chunk.
   */
  public int workAmount() {
    return 100;
  }
}
