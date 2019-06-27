package hex.generic;

import hex.*;
import hex.genmodel.attributes.*;
import hex.genmodel.descriptor.ModelDescriptor;
import water.util.Log;
import water.util.TwoDimTable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

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

    private void fillSharedTreeModelAttributes(final SharedTreeModelAttributes sharedTreeModelAttributes) {
        _variable_importances = convertVariableImportances(sharedTreeModelAttributes.getVariableImportances());
        convertMetrics(sharedTreeModelAttributes);
    }

    private void convertMetrics(final SharedTreeModelAttributes sharedTreeModelAttributes) {
        // Training metrics
        final MojoModelMetrics trainingMetrics = sharedTreeModelAttributes.getTrainingMetrics();
        final ModelMetrics modelMetrics = new ModelMetrics(null, null, trainingMetrics._nobs, trainingMetrics._MSE, trainingMetrics._description,
                new CustomMetric(trainingMetrics._custom_metric_name, trainingMetrics._custom_metric_value));
        _training_metrics = (ModelMetrics) convertObjects(sharedTreeModelAttributes.getTrainingMetrics(), modelMetrics);
    }


    @Override
    public ModelCategory getModelCategory() {
        return _modelCategory; // Might be calculated as well, but the information in MOJO is the one to display.
    }

    @Override
    public int nfeatures() {
        return _nfeatures;
    }

    private static Object convertObjects(final Object source, final Object target) {

        final Class<?> targetClass = target.getClass();
        final Field[] targetDeclaredFields = targetClass.getDeclaredFields();

        final Class<?> sourceClass = source.getClass();
        final Field[] sourceDeclaredFields = sourceClass.getDeclaredFields();
        
        // Create a map for faster search afterwards
        final Map<String, Field> sourceFieldMap = new HashMap(sourceDeclaredFields.length);
        for (Field sourceField : sourceDeclaredFields) {
            sourceFieldMap.put(sourceField.getName(), sourceField);
        }

        for (int i = 0; i < targetDeclaredFields.length; i++) {
            final Field targetField = targetDeclaredFields[i];
            final String targetFieldName = targetField.getName();
            final Field sourceField = sourceFieldMap.get(targetFieldName);
            if(sourceField == null) {
                Log.warn(String.format("Field '%s' not found in the source object. Ignoring.", targetFieldName));
                continue;
            }
            
            try{
                targetField.setAccessible(true);
                sourceField.setAccessible(true);
                if(targetField.getType().isAssignableFrom(sourceField.getType())){
                    targetField.set(target, sourceField.get(source));
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } finally {
                targetField.setAccessible(false);
                sourceField.setAccessible(false);
            }
            
        }


        return target;
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
