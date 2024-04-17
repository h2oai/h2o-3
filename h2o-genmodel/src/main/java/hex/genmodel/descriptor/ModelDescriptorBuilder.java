package hex.genmodel.descriptor;

import hex.ModelCategory;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.parameters.ColumnSpecifier;
import hex.genmodel.utils.ArrayUtils;

import java.io.Serializable;
import java.util.Arrays;

public class ModelDescriptorBuilder {

    /**
     * Builds an instance of {@link ModelDescriptor}, using information provided by the serialized model and
     * which corresponding implementations of {@link hex.genmodel.ModelMojoReader} are able to provide.
     *
     * @param mojoModel         A MojoModel to extract the model description from
     * @param fullAlgorithmName A full name of the algorithm
     * @param modelAttributes   Optional model attributes
     * @return A new instance of {@link ModelDescriptor}
     */
    public static ModelDescriptor makeDescriptor(final MojoModel mojoModel, final String fullAlgorithmName,
                                                 final ModelAttributes modelAttributes) {
        return new MojoModelDescriptor(mojoModel, fullAlgorithmName, modelAttributes);
    }

    public static ModelDescriptor makeDescriptor(final GenModel pojoModel) {
        return new PojoModelDescriptor(pojoModel);
    }

    public static class MojoModelDescriptor implements ModelDescriptor, Serializable {
        // Mandatory
        private final String _h2oVersion;
        private final hex.ModelCategory _category;
        private final String _uuid;
        private final boolean _supervised;
        private final int _nfeatures;
        private final int _nclasses;
        private final boolean _balanceClasses;
        private final double _defaultThreshold;
        private final double[] _priorClassDistrib;
        private final double[] _modelClassDistrib;
        private final String _offsetColumn;
        private final String _foldColumn;
        private final String _weightsColumn;
        private final String _treatmentColumn;
        private final String[][] _domains;
        private final String[][] _origDomains;
        private final String[] _names;
        private final String[] _origNames;
        private final String _algoName;
        private final String _fullAlgoName;

        private MojoModelDescriptor(final MojoModel mojoModel, final String fullAlgorithmName,
                                    final ModelAttributes modelAttributes) {
            _category = mojoModel._category;
            _uuid = mojoModel._uuid;
            _supervised = mojoModel.isSupervised();
            _nfeatures = mojoModel.nfeatures();
            _nclasses = mojoModel._nclasses;
            _balanceClasses = mojoModel._balanceClasses;
            _defaultThreshold = mojoModel._defaultThreshold;
            _priorClassDistrib = mojoModel._priorClassDistrib;
            _modelClassDistrib = mojoModel._modelClassDistrib;
            _h2oVersion = mojoModel._h2oVersion;
            _offsetColumn = mojoModel._offsetColumn;
            _foldColumn = mojoModel._foldColumn;
            _domains = mojoModel._domains;
            _origDomains = mojoModel.getOrigDomainValues();
            _names = mojoModel._names;
            _origNames = mojoModel.getOrigNames();
            _algoName = mojoModel._algoName;
            _fullAlgoName = fullAlgorithmName;
            if (modelAttributes != null) {
                ColumnSpecifier weightsColSpec = (ColumnSpecifier) modelAttributes.getParameterValueByName("weights_column");
                _weightsColumn = weightsColSpec != null ? weightsColSpec.getColumnName() : null; 
            } else {
                _weightsColumn = null;
            }
            if (modelAttributes != null) {
                _treatmentColumn = (String) modelAttributes.getParameterValueByName("treatment_column");;
            } else {
                _treatmentColumn = null;
            }
        }

        @Override
        public String[][] scoringDomains() {
            return _domains;
        }

        @Override
        public String projectVersion() {
            return _h2oVersion;
        }

        @Override
        public String algoName() {
            return _algoName;
        }

        @Override
        public String algoFullName() {
            return _fullAlgoName;
        }

        @Override
        public String offsetColumn() {
            return _offsetColumn;
        }

        @Override
        public String weightsColumn() {
            return _weightsColumn;
        }

        @Override
        public String foldColumn() {
            return _foldColumn;
        }

        @Override
        public String treatmentColumn() {
            return _treatmentColumn;
        }

        @Override
        public ModelCategory getModelCategory() {
            return _category;
        }

        @Override
        public boolean isSupervised() {
            return _supervised;
        }

        @Override
        public int nfeatures() {
            return _nfeatures;
        }

        @Override
        public String[] features() {
            return Arrays.copyOf(columnNames(), nfeatures());
        }

        @Override
        public int nclasses() {
            return _nclasses;
        }

        @Override
        public String[] columnNames() {
            return _names;
        }

        @Override
        public boolean balanceClasses() {
            return _balanceClasses;
        }

        @Override
        public double defaultThreshold() {
            return _defaultThreshold;
        }

        @Override
        public double[] priorClassDist() {
            return _priorClassDistrib;
        }

        @Override
        public double[] modelClassDist() {
            return _modelClassDistrib;
        }

        @Override
        public String uuid() {
            return _uuid;
        }

        @Override
        public String timestamp() {
            return null;
        }

        @Override
        public String[] getOrigNames() {
            return _origNames;
        }

        @Override
        public String[][] getOrigDomains() {
            return _origDomains;
        }
    }

    public static class PojoModelDescriptor implements ModelDescriptor, Serializable {
        // Mandatory
        private final hex.ModelCategory _category;
        private final boolean _supervised;
        private final int _nfeatures;
        private final int _nclasses;
        private final String _offsetColumn;
        private final String[][] _domains;
        private final String[][] _origDomains;
        private final String[] _names;
        private final String[] _origNames;

        private PojoModelDescriptor(final GenModel mojoModel) {
            _category = mojoModel.getModelCategory();
            _supervised = mojoModel.isSupervised();
            _nfeatures = mojoModel.nfeatures();
            _nclasses = mojoModel.getNumResponseClasses();
            _offsetColumn = mojoModel.getOffsetName();
            _domains = mojoModel.getDomainValues();
            _origDomains = mojoModel.getOrigDomainValues();
            String[] names = mojoModel.getNames();
            if (names.length == _domains.length - 1 && mojoModel.isSupervised() && 
                    !names[names.length - 1].equals(mojoModel._responseColumn)) {
                names = ArrayUtils.append(names, mojoModel._responseColumn);
            }
            _names = names;
            _origNames = mojoModel.getOrigNames();
        }

        @Override
        public String[][] scoringDomains() {
            return _domains;
        }

        @Override
        public String projectVersion() {
            return "unknown";
        }

        @Override
        public String algoName() {
            return "pojo";
        }

        @Override
        public String algoFullName() {
            return "POJO Scorer";
        }

        @Override
        public String offsetColumn() {
            return _offsetColumn;
        }

        @Override
        public String weightsColumn() {
            return null;
        }

        @Override
        public String foldColumn() {
            return null;
        }

        @Override
        public String treatmentColumn() { return null; }

        @Override
        public ModelCategory getModelCategory() {
            return _category;
        }

        @Override
        public boolean isSupervised() {
            return _supervised;
        }

        @Override
        public int nfeatures() {
            return _nfeatures;
        }

        @Override
        public String[] features() {
            return Arrays.copyOf(columnNames(), nfeatures());
        }

        @Override
        public int nclasses() {
            return _nclasses;
        }

        @Override
        public String[] columnNames() {
            return _names;
        }

        @Override
        public boolean balanceClasses() {
            return false;
        }

        @Override
        public double defaultThreshold() {
            return Double.NaN;
        }

        @Override
        public double[] priorClassDist() {
            return null;
        }

        @Override
        public double[] modelClassDist() {
            return null;
        }

        @Override
        public String uuid() {
            return null;
        }

        @Override
        public String timestamp() {
            return null;
        }

        @Override
        public String[] getOrigNames() {
            return _origNames;
        }

        @Override
        public String[][] getOrigDomains() {
            return _origDomains;
        }
    }

}
