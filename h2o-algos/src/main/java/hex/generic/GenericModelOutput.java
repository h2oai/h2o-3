package hex.generic;

import hex.*;
import hex.genmodel.attributes.*;
import hex.genmodel.attributes.KeyValue;
import hex.genmodel.attributes.metrics.*;
import hex.genmodel.descriptor.ModelDescriptor;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.util.Log;
import water.util.TwoDimTable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class GenericModelOutput extends Model.Output {

    final ModelCategory _modelCategory;
    final int _nfeatures;
    TwoDimTable _variable_importances;
    TwoDimTable _model_parameters;


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
        _cross_validation_metrics_summary = convertTable(modelAttributes.getCrossValidationMetricsSummary());
        
        if (modelAttributes != null && modelAttributes instanceof SharedTreeModelAttributes) {
            fillSharedTreeModelAttributes((SharedTreeModelAttributes) modelAttributes, modelDescriptor);
        } else {
            _variable_importances = null;
        }
        if(modelAttributes != null) {
            _model_parameters = convertModelParameters(modelAttributes.getModelParameters());
        }
        
        convertMetrics(modelAttributes, modelDescriptor);
        _scoring_history = convertTable(modelAttributes.getScoringHistory());

    }

    private void fillSharedTreeModelAttributes(final SharedTreeModelAttributes sharedTreeModelAttributes, final ModelDescriptor modelDescriptor) {
        _variable_importances = convertVariableImportances(sharedTreeModelAttributes.getVariableImportances());
    }

    private void convertMetrics(final ModelAttributes modelAttributes, final ModelDescriptor modelDescriptor) {
        // Training metrics

        if (modelAttributes.getTrainingMetrics() != null) {
            _training_metrics = (ModelMetrics) convertObjects(modelAttributes.getTrainingMetrics(),
                    determineModelmetricsType(modelAttributes.getTrainingMetrics(), modelDescriptor, modelAttributes));
        }
        if (modelAttributes.getValidationMetrics() != null) {
            _validation_metrics = (ModelMetrics) convertObjects(modelAttributes.getValidationMetrics(),
                    determineModelmetricsType(modelAttributes.getValidationMetrics(), modelDescriptor, modelAttributes));
        }
        if (modelAttributes.getCrossValidationMetrics() != null) {
            _cross_validation_metrics = (ModelMetrics) convertObjects(modelAttributes.getCrossValidationMetrics(),
                    determineModelmetricsType(modelAttributes.getCrossValidationMetrics(), modelDescriptor, modelAttributes));
        }
        
    }

    private ModelMetrics determineModelmetricsType(final MojoModelMetrics mojoMetrics, final ModelDescriptor modelDescriptor,
                                                   final ModelAttributes modelAttributes) {
        final ModelCategory modelCategory = modelDescriptor.getModelCategory();
        switch (modelCategory) {
            case Binomial:
                assert mojoMetrics instanceof MojoModelMetricsBinomial;
                final MojoModelMetricsBinomial binomial = (MojoModelMetricsBinomial) mojoMetrics;
                final AUC2 auc = AUC2.emptyAUC();
                auc._auc = binomial._auc;
                auc._pr_auc = binomial._pr_auc;
                auc._gini = binomial._gini;
                if (mojoMetrics instanceof MojoModelMetricsBinomialGLM) {
                    assert modelAttributes instanceof ModelAttributesGLM;
                    final ModelAttributesGLM modelAttributesGLM = (ModelAttributesGLM) modelAttributes;
                    final MojoModelMetricsBinomialGLM glmBinomial = (MojoModelMetricsBinomialGLM) binomial;
                    return new ModelMetricsBinomialGLMGeneric(null, null, mojoMetrics._nobs, mojoMetrics._MSE,
                            _domains[_domains.length - 1], glmBinomial._sigma,
                            auc, binomial._logloss, convertTable(binomial._gains_lift_table),
                            new CustomMetric(mojoMetrics._custom_metric_name, mojoMetrics._custom_metric_value), binomial._mean_per_class_error,
                            convertTable(binomial._thresholds_and_metric_scores), convertTable(binomial._max_criteria_and_metric_scores),
                            convertTable(binomial._confusion_matrix), glmBinomial._nullDegressOfFreedom, glmBinomial._residualDegressOfFreedom,
                            glmBinomial._resDev, glmBinomial._nullDev, glmBinomial._AIC, convertTable(modelAttributesGLM._coefficients_table),
                            glmBinomial._r2);
                } else {
                    return new ModelMetricsBinomialGeneric(null, null, mojoMetrics._nobs, mojoMetrics._MSE,
                            _domains[_domains.length - 1], binomial._sigma,
                            auc, binomial._logloss, convertTable(binomial._gains_lift_table),
                            new CustomMetric(mojoMetrics._custom_metric_name, mojoMetrics._custom_metric_value), binomial._mean_per_class_error,
                            convertTable(binomial._thresholds_and_metric_scores), convertTable(binomial._max_criteria_and_metric_scores),
                            convertTable(binomial._confusion_matrix), binomial._r2);
                }
            case Multinomial:
                assert mojoMetrics instanceof MojoModelMetricsMultinomial;

                if (mojoMetrics instanceof MojoModelMetricsMultinomialGLM) {
                    assert modelAttributes instanceof ModelAttributesGLM;
                    final ModelAttributesGLM modelAttributesGLM = (ModelAttributesGLM) modelAttributes;
                    final MojoModelMetricsMultinomialGLM glmMultinomial = (MojoModelMetricsMultinomialGLM) mojoMetrics;
                    return new ModelMetricsMultinomialGLMGeneric(null, null, mojoMetrics._nobs, mojoMetrics._MSE,
                            _domains[_domains.length - 1], glmMultinomial._sigma,
                            convertTable(glmMultinomial._confusion_matrix), convertTable(glmMultinomial._hit_ratios),
                            glmMultinomial._logloss, new CustomMetric(mojoMetrics._custom_metric_name, mojoMetrics._custom_metric_value),
                            glmMultinomial._mean_per_class_error, glmMultinomial._nullDegressOfFreedom, glmMultinomial._residualDegressOfFreedom,
                            glmMultinomial._resDev, glmMultinomial._nullDev, glmMultinomial._AIC, convertTable(modelAttributesGLM._coefficients_table),
                            glmMultinomial._r2);
                } else {
                    final MojoModelMetricsMultinomial multinomial = (MojoModelMetricsMultinomial) mojoMetrics;
                    return new ModelMetricsMultinomialGeneric(null, null, mojoMetrics._nobs, mojoMetrics._MSE,
                            _domains[_domains.length - 1], multinomial._sigma,
                            convertTable(multinomial._confusion_matrix), convertTable(multinomial._hit_ratios),
                            multinomial._logloss, new CustomMetric(mojoMetrics._custom_metric_name, mojoMetrics._custom_metric_value),
                            multinomial._mean_per_class_error, multinomial._r2);
                }
            case Regression:
                assert mojoMetrics instanceof MojoModelMetricsRegression;

                if (mojoMetrics instanceof MojoModelMetricsRegressionGLM) {
                    assert modelAttributes instanceof ModelAttributesGLM;
                    final ModelAttributesGLM modelAttributesGLM = (ModelAttributesGLM) modelAttributes;
                    final MojoModelMetricsRegressionGLM regressionGLM = (MojoModelMetricsRegressionGLM) mojoMetrics;
                    return new ModelMetricsRegressionGLMGeneric(null, null, regressionGLM._nobs, regressionGLM._MSE,
                            regressionGLM._sigma, regressionGLM._mae, regressionGLM._root_mean_squared_log_error, regressionGLM._mean_residual_deviance,
                            new CustomMetric(regressionGLM._custom_metric_name, regressionGLM._custom_metric_value), regressionGLM._r2,
                            regressionGLM._nullDegressOfFreedom, regressionGLM._residualDegressOfFreedom, regressionGLM._resDev,
                            regressionGLM._nullDev, regressionGLM._AIC, convertTable(modelAttributesGLM._coefficients_table));
                } else {
                    MojoModelMetricsRegression metricsRegression = (MojoModelMetricsRegression) mojoMetrics;

                    return new ModelMetricsRegressionGeneric(null, null, metricsRegression._nobs, metricsRegression._MSE,
                            metricsRegression._sigma, metricsRegression._mae, metricsRegression._root_mean_squared_log_error, metricsRegression._mean_residual_deviance,
                            new CustomMetric(mojoMetrics._custom_metric_name, mojoMetrics._custom_metric_value), metricsRegression._r2);
                }
            case AnomalyDetection:
                assert mojoMetrics instanceof MojoModelMetricsAnomaly;
                // There is no need to introduce new Generic alternatives to the original metric objects at the moment.
                // The total values can be simply calculated. The extra calculation time is negligible.
                MojoModelMetricsAnomaly metricsAnomaly = (MojoModelMetricsAnomaly) mojoMetrics;
                return new ModelMetricsAnomaly(null, null, new CustomMetric(mojoMetrics._custom_metric_name, mojoMetrics._custom_metric_value),
                        mojoMetrics._nobs, metricsAnomaly._mean_score * metricsAnomaly._nobs, metricsAnomaly._mean_normalized_score * metricsAnomaly._nobs,
                        metricsAnomaly._description);
            case Ordinal:
                assert mojoMetrics instanceof MojoModelMetricsOrdinal;

                if (mojoMetrics instanceof MojoModelMetricsOrdinalGLM) {
                    assert modelAttributes instanceof ModelAttributesGLM;
                    final ModelAttributesGLM modelAttributesGLM = (ModelAttributesGLM) modelAttributes;
                    MojoModelMetricsOrdinalGLM ordinalMetrics = (MojoModelMetricsOrdinalGLM) mojoMetrics;
                    return new ModelMetricsOrdinalGLMGeneric(null, null, ordinalMetrics._nobs, ordinalMetrics._MSE,
                            ordinalMetrics._domain, ordinalMetrics._sigma, convertTable(ordinalMetrics._cm), ordinalMetrics._hit_ratios,
                            ordinalMetrics._logloss, new CustomMetric(ordinalMetrics._custom_metric_name, ordinalMetrics._custom_metric_value),
                            ordinalMetrics._r2, ordinalMetrics._nullDegressOfFreedom, ordinalMetrics._residualDegressOfFreedom, ordinalMetrics._resDev,
                            ordinalMetrics._nullDev, ordinalMetrics._AIC, convertTable(modelAttributesGLM._coefficients_table), 
                            convertTable(ordinalMetrics._hit_ratio_table));
                } else {
                    MojoModelMetricsOrdinal ordinalMetrics = (MojoModelMetricsOrdinal) mojoMetrics;
                    return new ModelMetricsOrdinalGeneric(null, null, ordinalMetrics._nobs, ordinalMetrics._MSE,
                            ordinalMetrics._domain, ordinalMetrics._sigma, convertTable(ordinalMetrics._cm), ordinalMetrics._hit_ratios,
                            ordinalMetrics._logloss, new CustomMetric(ordinalMetrics._custom_metric_name, ordinalMetrics._custom_metric_value),
                            ordinalMetrics._r2, convertTable(ordinalMetrics._hit_ratio_table));
                }
            case Unknown:
            case Clustering:
            case AutoEncoder:
            case DimReduction:
            case WordEmbedding:
            case CoxPH:
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
        final Field[] targetDeclaredFields = targetClass.getFields();

        final Class<?> sourceClass = source.getClass();
        final Field[] sourceDeclaredFields = sourceClass.getFields();
        
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
                Log.err(e);
                continue;
            } finally {
                targetField.setAccessible(targetAccessible);
                sourceField.setAccessible(sourceAccessible);
            }
            
        }


        return target;
    }

    private static TwoDimTable convertModelParameters(final ModelParameter[] modelParameters) {
        if(modelParameters == null) return null;
        
        String[] parameterNames = new String[modelParameters.length];
        String[][] actualValStr = new String[modelParameters.length][1];
        double[][] actualValDouble = new double[modelParameters.length][1];
        for(int i = 0; i < modelParameters.length; i++) {
            parameterNames[i] = modelParameters[i]._name;
            Object actualValue = modelParameters[i].actual_value;
            String strReprForActualValue;
            if(actualValue instanceof String[]) {
                strReprForActualValue = Arrays.toString((String[])actualValue);
            } else if(actualValue instanceof Double[]) {
                strReprForActualValue = Arrays.toString((Double[])actualValue);
            } else if(actualValue instanceof Float[]) {
                strReprForActualValue = Arrays.toString((Float[])actualValue);
            } else if(actualValue instanceof VecSpecifier) {
                strReprForActualValue = ((VecSpecifier) actualValue)._column_name;
            } else if(actualValue instanceof Key) {
                strReprForActualValue = ((Key) actualValue)._name;
            } else if(actualValue instanceof KeyValue[]) {
                KeyValue[] keyValues = (KeyValue[]) actualValue;
                StringBuilder sb = new StringBuilder();
                for(int kvidx = 0; kvidx < keyValues.length; i++) {
                    sb.append(keyValues[kvidx]._key).append(":").append(keyValues[kvidx]._value); 
                    kvidx++;
                }
                strReprForActualValue = sb.toString();
            } else if(actualValue == null) {
                strReprForActualValue = "null";
            } else {
                strReprForActualValue = actualValue.toString();
            }
            actualValStr[i][0] = strReprForActualValue;
            actualValDouble[i] = null;
        }

        final TwoDimTable table = new TwoDimTable(
                "Model parameters",
                null,
                parameterNames,
                new String[]{ "actual_value"},
                new String[]{ "string"},
                new String[]{"%s"},
                "Parameter",
                actualValStr,  
                actualValDouble
                
        );
        return table;
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
                convertedTable.getColumnFormats(), convertedTable.getColHeaderForRowHeaders());

        for (int i = 0; i < convertedTable.columns(); i++) {
            for (int j = 0; j < convertedTable.rows(); j++) {
                table.set(j, i, convertedTable.getCell(i,j));
            }
        }

        return table;
    }
    
}
