package water.util;

import water.fvec.Frame;

public interface CategoricalEncoder {

  Frame encode(Frame fr, String[] skipCols);
  
  default Frame encode(Frame fr) {
    return encode(fr, new String[]{});
  }
  
}
