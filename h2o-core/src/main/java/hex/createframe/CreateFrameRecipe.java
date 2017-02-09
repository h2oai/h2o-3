package hex.createframe;

import water.H2O;
import water.Iced;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.util.Log;

import java.util.Random;

/**
 * Base class for all frame creation recipes.
 */
public abstract class CreateFrameRecipe<T extends CreateFrameRecipe<T>> extends Iced<T> {
  public Key<Frame> dest;
  public long seed = -1;

  //--------------------------------------------------------------------------------------------------------------------
  // Inheritance interface
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Test whether the input parameters are valid, and throw error if they
   * aren't. You may use the {@link #check(boolean, String)} helper function
   * to make this somewhat easier.
   */
  protected abstract void checkParametersValidity();

  /**
   * Set up the provided {@link CreateFrameExecutor} so that it knows how to
   * construct the frame corresponding to the recipe being built.
   */
  protected abstract void buildRecipe(CreateFrameExecutor cfe);


  //--------------------------------------------------------------------------------------------------------------------
  // Other
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * This function will be called by the REST API handler to initiate making
   * of the recipe. It returns a {@link Job} which will hold the created frame
   * once it is finished.
   */
  public Job<Frame> exec() {
    fillMissingParameters();
    Job<Frame> job = new Job<>(dest, Frame.class.getName(), "CreateFrame:original");
    CreateFrameExecutor cfe = new CreateFrameExecutor(job);
    checkParametersValidity();
    buildRecipe(cfe);
    checkParametersValidity2(cfe);
    return job.start(cfe, cfe.workAmount());
  }


  /**
   * Resolve parameter values that cannot be initialized to static defaults.
   * If you're overriding this method, please make sure to invoke the super
   * implementation as well.
   */
  protected void fillMissingParameters() {
    if (dest == null) {
      dest = Key.make();
    }
    if (seed == -1) {
      seed = new Random().nextLong();
      Log.info("Generated seed: " + seed);
    }
  }

  /**
   * Final step of parameter testing, after the {@link CreateFrameExecutor}
   * has been set up, but just before the actual frame creation commences.
   * This method shall only be used to perform checks that cannot be done
   * without the {@link CreateFrameExecutor} instance.
   */
  protected void checkParametersValidity2(CreateFrameExecutor cfe) {
    long byteEstimate = cfe.estimatedByteSize();
    long clusterFreeMem = H2O.CLOUD.free_mem();
    double gb = (double) (1 << 30);
    check(byteEstimate <= clusterFreeMem,
        String.format("Frame is expected to require %.3fGb, which will not fit into H2O's free memory of %.3fGb",
            byteEstimate/gb, clusterFreeMem/gb));
  }

  /** Simple helper function for parameter testing. */
  protected void check(boolean test, String msg) {
    if (!test) throw new IllegalArgumentException(msg);
  }
}
