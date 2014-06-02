package hex.schemas;

import hex.deeplearning.*;
import java.util.*;
import water.*;
import water.api.Handler;
import water.api.RequestServer;
import water.util.RString;

public class DeepLearningHandler extends Handler<DeepLearningHandler,DeepLearningV2> {
  // Inputs
  Key _src; // Key holding final value after job is removed

  // Output
  Key _job; // Boolean read-only value; exists==>running, not-exists==>canceled/removed

  // The model!
  DeepLearningModel _dlm;

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { 
    hex.deeplearning.DeepLearning dl = new hex.deeplearning.DeepLearning(Key.make("DeepLearn_Model"));
    dl.source = DKV.get(_src).get();
    dl.classification = true;
    dl.response = dl.source.lastVec();
    dl.exec();
    _dlm = DKV.get(dl.dest()).get();
  }

  // DL Schemas are at V2
  @Override protected DeepLearningV2 schema(int version) { return new DeepLearningV2(); }
}
