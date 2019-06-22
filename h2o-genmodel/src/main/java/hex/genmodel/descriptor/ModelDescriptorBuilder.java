package hex.genmodel.descriptor;

import com.google.protobuf.ProtocolStringList;
import hex.ModelCategory;
import hex.genmodel.ModelDescriptor;
import hex.genmodel.descriptor.models.Common;
import hex.genmodel.descriptor.models.Model;

import java.util.List;
import java.util.Map;

public class ModelDescriptorBuilder {
    
    private final Model.H2OModel _h2oModel;

    // Optional
    private VariableImportances _variableImportances = null;
    private Table _modelSummary = null;

    public ModelDescriptorBuilder(final Model.H2OModel h2oModel) {
        _h2oModel = h2oModel;
       
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
                // TODO: Make the call inexpensive ?
                final String[][] domains = new String[_h2oModel.getColumnNameCount()][];
                final Map<String, Common.StringList> domainsMap = _h2oModel.getDomainsMap();
                final ProtocolStringList columnNameList = _h2oModel.getColumnNameList();

                for (int i = 0; i < columnNameList.size(); i++) {
                    final Common.StringList columnDomain = domainsMap.get(columnNameList.get(i));
                    if(columnDomain != null){
                        domains[i] = columnDomain.getValueList().toArray(new String[0]);
                    }
                }
                return domains;
            }

            @Override
            public String projectVersion() {
                return _h2oModel.getProjectVersion();
            }

            @Override
            public String algoName() {
                 return _h2oModel.getAlgorithmShortcut();
            }

            @Override
            public String algoFullName() {
                return _h2oModel.getAlgorithmFullName();
            }

            @Override
            public String offsetColumn() {
                final String offsetColumn = _h2oModel.getOffsetColumn();
                return offsetColumn.isEmpty() ? null : offsetColumn;
            }

            @Override
            public String weightsColumn() {
                final String weightsColumn = _h2oModel.getWeightsColumn();
                return weightsColumn.isEmpty() ? null : weightsColumn;
            }

            @Override
            public String foldColumn() {
                final String foldColumn = _h2oModel.getFoldColumn();
                return foldColumn.isEmpty() ? null : foldColumn;
            }

            @Override
            public ModelCategory getModelCategory() {
                return ModelCategory.valueOf(_h2oModel.getCategory().toString()) ;
            }

            @Override
            public boolean isSupervised() {
                return _h2oModel.getSupervised();
            }

            @Override
            public int nfeatures() {
                return _h2oModel.getFeaturesCount();
            }

            @Override
            public String[] features() {
                return _h2oModel.getFeaturesNameList().toArray(new String[0]);
            }

            @Override
            public int nclasses() {
                return _h2oModel.getClassesCount();
            }

            @Override
            public String[] columnNames() {
                return _h2oModel.getColumnNameList().toArray(new String[0]);
            }

            @Override
            public boolean balanceClasses() {
                return _h2oModel.getClassesBalanced();
            }

            @Override
            public double defaultThreshold() {
                return _h2oModel.getDefaultThreshold();
            }

            @Override
            public double[] priorClassDist() {
                // TODO: make the call inexpensive ?
                final List<Double> aprioriClassDistributionsList = _h2oModel.getAprioriClassDistributionsList();
                final double[] elements = new double[aprioriClassDistributionsList.size()];
                for (int i = 0; i < aprioriClassDistributionsList.size(); i++) {
                    elements[i] = aprioriClassDistributionsList.get(i);
                }
                
                return elements;
            }

            @Override
            public double[] modelClassDist() {
                // TODO: make the call inexpensive ?
                final List<Double> modelClassDistributionsList = _h2oModel.getModelClassDistributionsList();
                final double[] elements = new double[modelClassDistributionsList.size()];
                for (int i = 0; i < modelClassDistributionsList.size(); i++) {
                    elements[i] = modelClassDistributionsList.get(i);
                }

                return elements;
            }

            @Override
            public String uuid() {
                return _h2oModel.getUuid();
            }

            @Override
            public String timestamp() {
                return _h2oModel.getCreated().toString();
            }

            @Override
            public VariableImportances variableImportances() {
                return null;
            }

            @Override
            public Table modelSummary() {
                return _modelSummary;
            }
        };
    }
}
