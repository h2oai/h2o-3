package hex.api;

import hex.mli.loco.LeaveOneCovarOut;
import hex.schemas.LeaveOneCovarOutV3;
import water.H2O;
import water.Job;
import water.api.FramesHandler;
import hex.Model;
import water.api.schemas3.JobV3;
import water.fvec.Frame;
import water.api.Handler;
import water.api.ModelsHandler;
import water.Key;
import water.DKV;

public class LeaveOneCovarOutHandler extends Handler {

    public JobV3 getLoco(int version, final LeaveOneCovarOutV3 args) {

        final Frame frame = FramesHandler.getFromDKV("frame", args.frame.key());
        final Model model= ModelsHandler.getFromDKV("model",args.model.key());
        String loco_frame_id = args.loco_frame_id;
        final String replace_val = args.replace_val;
        assert model.isSupervised() : "Model " + model._key + " is not supervised.";

        if(loco_frame_id == null){
            loco_frame_id = "loco_"+frame._key.toString() + "_" + model._key.toString();
        }

        final Job<Frame> j = new Job(Key.make(loco_frame_id), Frame.class.getName(), "loco_prediction");
        final String dest_frame_id = loco_frame_id;
        H2O.H2OCountedCompleter work = new H2O.H2OCountedCompleter() {
                @Override
                public void compute2() {
                    LeaveOneCovarOut.leaveOneCovarOut(model,frame,j,replace_val,Key.make(dest_frame_id));
                    tryComplete();
                }
            };

        j.start(work, model._output._names.length);
        return new JobV3().fillFromImpl(j);
    }

}
