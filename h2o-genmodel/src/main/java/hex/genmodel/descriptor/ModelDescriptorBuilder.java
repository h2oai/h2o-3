package hex.genmodel.descriptor;

import hex.ModelCategory;
import hex.genmodel.ModelDescriptor;
import hex.genmodel.MojoModel;

import java.util.Arrays;

public class ModelDescriptorBuilder {
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
    private final String[][] _domains;
    private final String[] _names;
    private final String _algoName;

    // Optional
    private VariableImportances _variableImportances = null;
    private Table _modelSummary = null;

    public ModelDescriptorBuilder(final MojoModel mojoModel) {
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
        _domains = mojoModel._domains;
        _names = mojoModel._names;
        _algoName = mojoModel.getClass().getName();
    }

    public ModelDescriptorBuilder variableImportances(final VariableImportances variableImportances) {
        _variableImportances = variableImportances;
        return this;
    }

    public ModelDescriptorBuilder modelSummary(final Table modelSummary) {
        _modelSummary = modelSummary;
        return this;
    }

    /**
     * Builds the final instance of {@link ModelDescriptor}, using information provided by the serialized model and
     * which corresponding implementations of {@link hex.genmodel.ModelMojoReader} are able to provide.
     *
     * @return A new instance of {@link ModelDescriptor}
     */
    public ModelDescriptor build() {
        return new ModelDescriptor() {
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
                return _algoName;
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
            public VariableImportances variableImportances() {
                return _variableImportances;
            }

            @Override
            public Table modelSummary() {
                return _modelSummary;
            }
        };
    }
}
