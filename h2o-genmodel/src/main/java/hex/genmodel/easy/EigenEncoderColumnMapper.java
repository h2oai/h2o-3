package hex.genmodel.easy;

import hex.genmodel.GenModel;

import java.util.HashMap;
import java.util.Map;

public class EigenEncoderColumnMapper {

  protected final GenModel _m;

  public EigenEncoderColumnMapper(GenModel m) {
    _m = m;
  }

  public String[] getModelColumnNames() {
    return _m.getOrigNames();
  }
  
  public Map<String, Integer> create() {
    String[] modelColumnNames = getModelColumnNames();
    Map<String, Integer> modelColumnNameToIndexMap = new HashMap<>(modelColumnNames.length);
    for (int i = 0; i < modelColumnNames.length; i++) {
      modelColumnNameToIndexMap.put(modelColumnNames[i], i);
    }
    return modelColumnNameToIndexMap;
  }
}
