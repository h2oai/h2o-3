package water.api;

import hex.CreateFrame;
import hex.createframe.recipes.SimpleCreateFrameRecipe;
import water.Job;
import water.Key;
import water.api.schemas3.CreateFrameV3;
import water.api.schemas3.JobV3;
import water.api.schemas3.KeyV3;
import water.api.schemas4.input.CreateFrameSimpleIV4;
import water.api.schemas4.output.JobV4;
import water.fvec.Frame;

public class CreateFrameHandler extends Handler {

  public JobV3 run(int version, CreateFrameV3 cf) {
    if (cf.dest == null) {
      cf.dest = new KeyV3.FrameKeyV3();
      cf.dest.name = Key.rand();
    }


    CreateFrame cfr = new CreateFrame(cf.dest.key());
    cf.fillImpl(cfr);
    return new JobV3(cfr.execImpl());
  }


  public static class CreateSimpleFrame extends RestApiHandler<CreateFrameSimpleIV4, JobV4> {

    @Override public String name() {
      return "createSimpleFrame";
    }

    @Override public String help() {
      return "Create frame with random (uniformly distributed) data. You can specify " +
             "how many columns of each type to make; and what the desired range for " +
             "each column type.";
    }

    @Override
    public JobV4 exec(int ignored, CreateFrameSimpleIV4 input) {
      SimpleCreateFrameRecipe cf = input.createAndFillImpl();
      Job<Frame> job = cf.exec();
      return new JobV4().fillFromImpl(job);
    }
  }
}
