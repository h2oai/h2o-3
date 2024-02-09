package hex.pipeline;

import water.DKV;
import water.Job;
import water.Key;
import water.Scope;
import water.fvec.Frame;

public final class PipelineHelper {

  private PipelineHelper() {}

  public static Frame reassign(Frame fr, Key<Frame> key) {
    return reassign(fr, key, null);
  }
  
  public static Frame reassign(Frame fr, Key<Frame> key, Key<Job> job) {
    Frame copy = new Frame(fr);
    DKV.remove(key);
    copy._key = key;
    copy.write_lock(job);
    copy.update(job);
    return copy;
  }
  
  public static void reassignInplace(Frame fr, Key<Frame> key) {
    reassignInplace(fr, key, null);
  }
  
  public static void reassignInplace(Frame fr, Key<Frame> key, Key<Job> job) {
    assert DKV.get(key) == null || DKV.getGet(key) == null; // inplace reassignment only to fresh/unassigned keys
    if (fr.getKey() != null) DKV.remove(fr.getKey());
    fr._key = key;
    DKV.put(fr);
  }
}
