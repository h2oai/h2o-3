package ai.h2o.cascade.core;

import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Single column inside a {@link CFrame}.
 * <p>
 * This is a wrapper around a Vec, with the name attached. The column may also
 * carry a reference to the source {@link Frame}, if it was extracted from a
 * frame and not constructed on the fly.
 */
public class CFrameColumn {
  Frame parent;
  String name;
  Vec vec;

  public CFrameColumn(Frame f, int colIndex) {
    parent = f;
    name = f.name(colIndex);
    vec = f.vec(colIndex);
  }


  /**
   * Return the type of the column, one of the {@code T_*} constants defined
   * in {@link Vec}.
   */
  public byte type() {
    return vec.get_type();
  }

}
