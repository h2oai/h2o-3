package water.serial;

import java.io.IOException;

import water.AutoBuffer;
import water.Freezable;

public class AutoBufferSerializer<T extends Freezable> implements Serializer<T, AutoBuffer> {

  @Override public void save(T m, AutoBuffer output) {
    postSave(m,
            m.write(
              preSave(m,output)));
  }
  @Override public T load(T e, AutoBuffer input) {
    // Check model compatibility
    T r = (T) e.read(preLoad(e,input));
    postLoad(r,input);
    return e;
  }
  @Override public T load(AutoBuffer input) throws IOException {
    throw new UnsupportedOperationException("Loading from AutoBuffer requires a target object!");
  }

  /** Hook which is call before the model is serialized. */
  protected AutoBuffer preSave (T m, AutoBuffer ab) { return ab; }
  /** Hook which is call after the model is serialized. */
  protected AutoBuffer postSave(T m, AutoBuffer ab) { return ab; }
  /** Hook which is call before the model is loaded from <code>AutoBuffer</code>. */
  protected AutoBuffer preLoad (T m, AutoBuffer ab) { return ab; }
  /** Hook which is call after the model is loaded from <code>AutoBuffer</code>. */
  protected AutoBuffer postLoad(T m, AutoBuffer ab) { return ab; }
}
