package hex.mojo.api;

import hex.mojo.MojoDelegating;
import hex.mojo.MojoDelegatingModel;
import hex.mojo.MojoDelegatingModelParameters;
import water.Job;
import water.api.Handler;

public class MojoDelegatingModelHandler extends Handler {

    public MojoDelegatingModelV3 createMojoDelegatingModel(int version, MojoDelegatingModelV3 mojoDelegatingModelV3) {
        final MojoDelegatingModelParameters parameters = new MojoDelegatingModelParameters();
        parameters.mojoFile = mojoDelegatingModelV3.mojo_file_path;

        final MojoDelegating mojoDelegating = new MojoDelegating(parameters);
        mojoDelegating.init(false);
        final Job<MojoDelegatingModel> mojoDelegatingModelJob = mojoDelegating.trainModel();

        return mojoDelegatingModelV3;
    }

}
