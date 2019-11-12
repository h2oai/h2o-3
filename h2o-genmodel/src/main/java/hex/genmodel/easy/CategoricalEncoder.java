package hex.genmodel.easy;

import java.io.Serializable;

public interface CategoricalEncoder extends Serializable {

  /**
   * Encodes a given categorical level into a raw array onto the right position.
   * @param level categorical level
   * @param rawData raw input to score0
   * @return true if provided categorical level was valid and was properly encoded, false if nothing was written to raw data
   */
  boolean encodeCatValue(String level, double[] rawData);

  /**
   * Encode NA (missing level) into raw data.
   * @param rawData target raw data array
   */
  void encodeNA(double[] rawData);
  
}
