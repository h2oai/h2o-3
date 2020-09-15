package hex.genmodel;

import hex.genmodel.easy.*;

import java.util.Map;

public enum CategoricalEncodings implements CategoricalEncoding {
    AUTO {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new EnumEncoderColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new EnumEncoderDomainMapConstructor(m, columnToOffset).create();
        }
    },
    OneHotExplicit {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new OneHotEncoderColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new OneHotEncoderDomainMapConstructor(m, columnToOffset).create();
        }
    },
    Binary {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new BinaryColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new BinaryDomainMapConstructor(m, columnToOffset).create();
        }
    },
    EnumLimited {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new EnumLimitedEncoderColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new EnumLimitedEncoderDomainMapConstructor(m, columnToOffset).create();
        }
    },
    Eigen {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new EigenEncoderColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new EigenEncoderDomainMapConstructor(m, columnToOffset).create();
        }
    },
    LabelEncoder {
        @Override
        public Map<String, Integer> createColumnMapping(GenModel m) {
            return new EnumEncoderColumnMapper(m).create();
        }

        @Override
        public Map<Integer, CategoricalEncoder> createCategoricalEncoders(GenModel m, Map<String, Integer> columnToOffset) {
            return new LabelEncoderDomainMapConstructor(m, columnToOffset).create();
        }
    };
}
