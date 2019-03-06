package hex.mojo;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.genmodel.ModelDescriptor;
import hex.genmodel.descriptor.Table;
import hex.genmodel.descriptor.VariableImportances;
import water.util.TwoDimTable;

public class GenericModelOutput extends Model.Output {
    
    private final ModelCategory _modelCategory;
    private final int _nfeatures;
    public final TwoDimTable _variable_importances;
    

    public GenericModelOutput(final ModelDescriptor modelDescriptor) {
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
        
        _variable_importances = readVariableImportances(modelDescriptor.variableImportances());
        _model_summary = convertTable(modelDescriptor.modelSummary());
    }

    @Override
    public ModelCategory getModelCategory() {
        return _modelCategory;
    }

    @Override
    public int nfeatures() {
        return _nfeatures;
    }
    
    private static TwoDimTable readVariableImportances(final VariableImportances variableImportances){
        if(variableImportances == null) return null;

        TwoDimTable varImps = ModelMetrics.calcVarImp(variableImportances.getImportances(), variableImportances.getVariableNames());
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
