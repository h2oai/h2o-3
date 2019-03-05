package hex.mojo;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.genmodel.ModelDescriptor;
import hex.genmodel.descriptor.VariableImportances;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Random;

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
}
