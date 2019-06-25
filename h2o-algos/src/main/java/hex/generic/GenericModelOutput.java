package hex.generic;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.genmodel.ModelDescriptor;
import hex.genmodel.MojoModel;
import hex.genmodel.descriptor.Table;
import hex.genmodel.descriptor.VariableImportances;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.Map;

public class GenericModelOutput extends Model.Output {
    
    private final ModelCategory _modelCategory;
    private final int _nfeatures;
    public final TwoDimTable _variable_importances;
    public final TwoDimTable[] _generic_tables;


    public GenericModelOutput(final MojoModel mojoModel) {
        final ModelDescriptor modelDescriptor = mojoModel._modelDescriptor; // TODO : Could be removed in future or not.

        final hex.genmodel.descriptor.models.Model.H2OModel h2oModel = mojoModel.h2oModel;

        _isSupervised = h2oModel.getSupervised();
        _domains = modelDescriptor.scoringDomains();
        _origDomains = modelDescriptor.scoringDomains();
        _hasOffset = !h2oModel.getOffsetColumn().isEmpty();
        _hasWeights = !h2oModel.getWeightsColumn().isEmpty();
        _hasFold = !h2oModel.getFoldColumn().isEmpty();
        _distribution = modelDescriptor.modelClassDist();
        _priorClassDist = ArrayUtils.toPrimitive(h2oModel.getAprioriClassDistributionsList());
        _names = h2oModel.getColumnNameList().toArray(new String[0]);
        
        _modelCategory = modelDescriptor.getModelCategory();
        _nfeatures = modelDescriptor.nfeatures();

        _variable_importances = readVariableImportances(h2oModel.getVariableImportancesMap());
        _generic_tables = new TwoDimTable[]{_variable_importances, _variable_importances}; // POC
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

    private static TwoDimTable readVariableImportances(final Map<String, Double> variableImportances) {
        if(variableImportances == null) return null;


        TwoDimTable varImps = ModelMetrics.calcVarImp(ArrayUtils.toPrimitive(variableImportances.values()),
                ArrayUtils.toArray(variableImportances.keySet()));
        return varImps;
    }
    
    private static TwoDimTable convertTable(final Table convertedTable){
        // TODO: Convertible to ProtoBuf classes as well, serves as a compatibility showcase 
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
