package hex.createframe;

import water.fvec.Frame;

import java.util.Random;

/**
 */
public abstract class CreateFramePostprocessStep {

  public abstract void exec(Frame fr, Random rng);

}
