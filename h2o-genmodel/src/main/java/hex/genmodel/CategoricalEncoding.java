package hex.genmodel;

import hex.genmodel.easy.*;

import java.util.Map;

public interface CategoricalEncoding {
  
  String name();

  Map<String, Integer> createColumnMapping(GenModel m);

  Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset);

}
