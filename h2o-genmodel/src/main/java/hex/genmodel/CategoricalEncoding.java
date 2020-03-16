package hex.genmodel;

import hex.genmodel.easy.*;

import java.util.Map;

public enum CategoricalEncoding {
  AUTO {
    @Override
    public Map<String, Integer> createColumnMapping(GenModel m) {
      return new EnumEncoderColumnMapper(m).create();
    }

    @Override
    public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping) {
      return new EnumEncoderDomainMapConstructor(m, columnMapping).create();
    }
  },
  OneHotExplicit {
    @Override
    public Map<String, Integer> createColumnMapping(GenModel m) {
      return new OneHotEncoderColumnMapper(m).create();
    }

    @Override
    public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping) {
      return new OneHotEncoderDomainMapConstructor(m, columnMapping).create();
    }
  },
  EnumLimited {
    @Override
    public Map<String, Integer> createColumnMapping(GenModel m) {
      return new EnumLimitedEncoderColumnMapper(m).create();
    }

    @Override
    public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping) {
      return new EnumLimitedEncoderDomainMapConstructor(m, columnMapping).create();
    }
  };

  public abstract Map<String, Integer> createColumnMapping(GenModel m);

  public abstract Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping);
  
}
