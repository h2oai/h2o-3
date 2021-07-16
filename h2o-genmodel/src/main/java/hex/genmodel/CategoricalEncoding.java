package hex.genmodel;

import hex.genmodel.easy.*;

import java.util.Map;

public enum CategoricalEncoding {
  AUTO(false) {
    @Override
    public Map<String, Integer> createColumnMapping(GenModel m) {
      return new EnumEncoderColumnMapper(m).create();
    }

    @Override
    public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping) {
      return new EnumEncoderDomainMapConstructor(m, columnMapping).create();
    }
  },
  OneHotExplicit(false) {
    @Override
    public Map<String, Integer> createColumnMapping(GenModel m) {
      return new OneHotEncoderColumnMapper(m).create();
    }

    @Override
    public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping) {
      return new OneHotEncoderDomainMapConstructor(m, columnMapping).create();
    }
  },
  Binary(false) {
    @Override
    public Map<String, Integer> createColumnMapping(GenModel m) {
      return new BinaryColumnMapper(m).create();
    }

    @Override
    public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping) {
      return new BinaryDomainMapConstructor(m, columnMapping).create();
    }
  },
  EnumLimited(true) {
    @Override
    public Map<String, Integer> createColumnMapping(GenModel m) {
      return new EnumLimitedEncoderColumnMapper(m).create();
    }

    @Override
    public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping) {
      return new EnumLimitedEncoderDomainMapConstructor(m, columnMapping).create();
    }
  },
  Eigen(true) {
    @Override
    public Map<String, Integer> createColumnMapping(GenModel m) {
      return new EigenEncoderColumnMapper(m).create();
    }

    @Override
    public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping) {
      return new EigenEncoderDomainMapConstructor(m, columnMapping).create();
    }
  },
  LabelEncoder(false) {
    @Override
    public Map<String, Integer> createColumnMapping(GenModel m) {
      return new EnumEncoderColumnMapper(m).create();
    }

    @Override
    public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping) {
      return new LabelEncoderDomainMapConstructor(m, columnMapping).create();
    }
  };

  private final boolean _parametrized;

  CategoricalEncoding(boolean parametrized) {
    _parametrized = parametrized;
  }

  public abstract Map<String, Integer> createColumnMapping(GenModel m);

  public abstract Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnMapping);

  /**
   * Does the categorical encoding have any parameters that are needed to correctly interpret it?
   * Eg.: number of classes for EnumLimited
   * 
   * @return Is this encoding parametrized?
   */
  public boolean isParametrized() {
    return _parametrized;
  }

}
