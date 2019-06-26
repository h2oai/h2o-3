package hex.generic;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.SharedTreeModelAttributes;
import hex.genmodel.descriptor.ModelDescriptor;
import hex.genmodel.attributes.Table;
import hex.genmodel.attributes.VariableImportances;
import water.util.TwoDimTable;

public class GenericModelOutput extends Model.Output {

    final ModelCategory _modelCategory;
    final int _nfeatures;
    TwoDimTable _variable_importances;


    public GenericModelOutput(final ModelDescriptor modelDescriptor, final ModelAttributes modelAttributes) {
        _isSupervised = modelDescriptor.isSupervised();
        _domains = modelDescriptor.scoringDomains();
        _origDomains = modelDescriptor.scoringDomains();
        _hasOffset = modelDescriptor.offsetColumn() != null;
        _hasWeights = modelDescriptor.weightsColumn() != null;
        _hasFold = modelDescriptor.foldColumn() != null;
        _distribution = modelDescriptor.modelClassDist();
        _priorClassDist = modelDescriptor.priorClassDist();
        _names = modelDescriptor.columnNames();
        _modelCategory = modelDescriptor.getModelCategory();
        _nfeatures = modelDescriptor.nfeatures();
        _model_summary = convertTable(modelAttributes.getModelSummary());

        if (modelAttributes instanceof SharedTreeModelAttributes) {
            fillSharedTreeModelAttributes((SharedTreeModelAttributes) modelAttributes);
        } else {
            _variable_importances = null;
        }
    }

    private void fillSharedTreeModelAttributes(SharedTreeModelAttributes sharedTreeModelAttributes) {
        _variable_importances = convertVariableImportances(sharedTreeModelAttributes.getVariableImportances());
    }


    @Override
    public ModelCategory getModelCategory() {
        return _modelCategory; // Might be calculated as well, but the information in MOJO is the one to display.
    }

    @Override
    public int nfeatures() {
        return _nfeatures;
    }

    private static TwoDimTable convertVariableImportances(final VariableImportances variableImportances) {
        if(variableImportances == null) return null;

        TwoDimTable varImps = ModelMetrics.calcVarImp(variableImportances._importances, variableImportances._variables);
        return varImps;
    }
    
    private static TwoDimTable convertTable(final Table convertedTable){
        if(convertedTable == null) return null;
        final TwoDimTable table = new TwoDimTable(convertedTable.getTableHeader(), convertedTable.getTableDescription(),
                convertedTable.getRowHeaders(), convertedTable.getColHeaders(), convertedTable.getColTypesString(),
                null, convertedTable.getColHeaderForRowHeaders());

        for (int i = 0; i < convertedTable.columns(); i++) {
            for (int j = 0; j < convertedTable.rows(); j++) {
                table.set(j, i, convertedTable.getCell(i,j));
            }
        }
        
        return table;
    }
}
