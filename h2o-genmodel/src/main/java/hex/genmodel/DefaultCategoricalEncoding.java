package hex.genmodel;

import hex.genmodel.easy.*;

import java.util.Map;

public enum DefaultCategoricalEncoding implements CategoricalEncoding {
    AUTO(false) {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new EnumEncoderColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new EnumEncoderDomainMapConstructor(m, columnToOffset).create();
        }
    },
    OneHotExplicit(false) {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new OneHotEncoderColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new OneHotEncoderDomainMapConstructor(m, columnToOffset).create();
        }
    },
    Binary(false) {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new BinaryColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new BinaryDomainMapConstructor(m, columnToOffset).create();
        }
    },
    EnumLimited(true) {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new EnumLimitedEncoderColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new EnumLimitedEncoderDomainMapConstructor(m, columnToOffset).create();
        }
    },
    Eigen(true) {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new EigenEncoderColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new EigenEncoderDomainMapConstructor(m, columnToOffset).create();
        }
    },
    LabelEncoder(false) {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new EnumEncoderColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new LabelEncoderDomainMapConstructor(m, columnToOffset).create();
        }
    };

    private final boolean _parametrized;

    DefaultCategoricalEncoding(boolean parametrized) {
        _parametrized = parametrized;
    }

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
