package hex.generic;

import hex.*;
import hex.genmodel.attributes.*;
import hex.genmodel.attributes.metrics.MojoModelMetrics;
import hex.genmodel.attributes.metrics.MojoModelMetricsBinomial;
import hex.genmodel.descriptor.ModelDescriptor;
import water.util.Log;
import water.util.TwoDimTable;

import java.lang.reflect.Field;
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
            fillSharedTreeModelAttributes((SharedTreeModelAttributes) modelAttributes, modelDescriptor);
        } else {
            _variable_importances = null;
            _training_metrics = null;
        }
    }

    private void fillSharedTreeModelAttributes(final SharedTreeModelAttributes sharedTreeModelAttributes, final ModelDescriptor modelDescriptor) {
        _variable_importances = convertVariableImportances(sharedTreeModelAttributes.getVariableImportances());
        convertMetrics(sharedTreeModelAttributes, modelDescriptor);
    }

    private void convertMetrics(final SharedTreeModelAttributes sharedTreeModelAttributes, final ModelDescriptor modelDescriptor) {
        // Training metrics
        final MojoModelMetrics trainingMetrics = sharedTreeModelAttributes.getTrainingMetrics();
        final ModelMetrics modelMetrics = determineModelmetricsType(trainingMetrics, modelDescriptor);
        _training_metrics = (ModelMetrics) convertObjects(sharedTreeModelAttributes.getTrainingMetrics(), modelMetrics);
    }

    private ModelMetrics determineModelmetricsType(final MojoModelMetrics mojoMetrics, final ModelDescriptor modelDescriptor) {
        final ModelCategory modelCategory = modelDescriptor.getModelCategory();
        switch (modelCategory) {
            case Unknown:
                return new ModelMetrics(null, null, mojoMetrics._nobs, mojoMetrics._MSE, mojoMetrics._description,
                        new CustomMetric(mojoMetrics._custom_metric_name, mojoMetrics._custom_metric_value));
            case Binomial:
                assert mojoMetrics instanceof MojoModelMetricsBinomial;
                final MojoModelMetricsBinomial binomial = (MojoModelMetricsBinomial) mojoMetrics;
                final AUC2 auc = AUC2.emptyAUC();
                auc._auc = binomial._auc;
                auc._pr_auc = binomial._pr_auc;
                auc._gini = binomial._gini;
                return new ModelMetricsBinomial(null, null, mojoMetrics._nobs, mojoMetrics._MSE,
                        modelDescriptor.scoringDomains()[modelDescriptor.nfeatures() - 1], 0D,
                        auc, binomial._logloss, convertTable(binomial._gains_lift_table),
                        new CustomMetric(mojoMetrics._custom_metric_name, mojoMetrics._custom_metric_value), binomial._mean_per_class_error,
                        convertTable(binomial._thresholds_and_metric_scores), convertTable(binomial._max_criteria_and_metric_scores),
                        convertTable(binomial._confusion_matrix));
            case Multinomial:
            case Ordinal:
            case Regression:
            case Clustering:
            case AutoEncoder:
            case DimReduction:
            case WordEmbedding:
            case CoxPH:
            case AnomalyDetection:
            default:
                return new ModelMetrics(null, null, mojoMetrics._nobs, mojoMetrics._MSE, mojoMetrics._description,
                        new CustomMetric(mojoMetrics._custom_metric_name, mojoMetrics._custom_metric_value));
        }
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
                Log.debug(String.format("Field '%s' not found in the source object. Ignoring.", targetFieldName));
                continue;
            }

            final boolean targetAccessible = targetField.isAccessible();
            final boolean sourceAccessible = sourceField.isAccessible();
            try{
                targetField.setAccessible(true);
                sourceField.setAccessible(true);
                if(targetField.getType().isAssignableFrom(sourceField.getType())){
                    targetField.set(target, sourceField.get(source));
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } finally {
                targetField.setAccessible(targetAccessible);
                sourceField.setAccessible(sourceAccessible);
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
