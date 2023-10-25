package hex.generic;

import hex.*;
import hex.genmodel.attributes.*;
import hex.genmodel.attributes.metrics.*;
import hex.genmodel.descriptor.ModelDescriptor;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.TwoDimTable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class GenericModelOutput extends Model.Output {
    public final String _original_model_identifier;
    public final String _original_model_full_name;
    public final ModelCategory _modelCategory;
    public final int _nfeatures;
    public final double _defaultThreshold;
    public TwoDimTable _variable_importances;

    public GenericModelOutput(final ModelDescriptor modelDescriptor) {
        _isSupervised = modelDescriptor.isSupervised();
        _domains = modelDescriptor.scoringDomains();
        _origDomains = modelDescriptor.getOrigDomains();
        _hasOffset = modelDescriptor.offsetColumn() != null;
        _hasWeights = modelDescriptor.weightsColumn() != null;
        _hasFold = modelDescriptor.foldColumn() != null;
        _hasTreatment = modelDescriptor.treatmentColumn() != null;
        _modelClassDist = modelDescriptor.modelClassDist();
        _priorClassDist = modelDescriptor.priorClassDist();
        _names = modelDescriptor.columnNames();
        _origNames = modelDescriptor.getOrigNames();
        _modelCategory = modelDescriptor.getModelCategory();
        _nfeatures = modelDescriptor.nfeatures();
        _defaultThreshold = modelDescriptor.defaultThreshold();
        _original_model_identifier = modelDescriptor.algoName();
        _original_model_full_name = modelDescriptor.algoFullName();

    }

    public GenericModelOutput(final ModelDescriptor modelDescriptor, final ModelAttributes modelAttributes, 
                              final Table[] reproducibilityInformation) {
        this(modelDescriptor);

        if (modelAttributes != null) {
            _model_summary = convertTable(modelAttributes.getModelSummary());
            _cross_validation_metrics_summary = convertTable(modelAttributes.getCrossValidationMetricsSummary());

            if (modelAttributes instanceof SharedTreeModelAttributes) {
                _variable_importances = convertVariableImportances(((SharedTreeModelAttributes) modelAttributes).getVariableImportances());
            } else if (modelAttributes instanceof DeepLearningModelAttributes) {
                _variable_importances = convertVariableImportances(((DeepLearningModelAttributes) modelAttributes).getVariableImportances());
            } else if (modelAttributes instanceof ModelAttributesGLM) {
                _variable_importances = convertVariableImportances(((ModelAttributesGLM) modelAttributes).getVariableImportances());
            } else {
                _variable_importances = null;
            }
            convertMetrics(modelAttributes, modelDescriptor);
            _scoring_history = convertTable(modelAttributes.getScoringHistory());
        }
        if (reproducibilityInformation != null) {
            _reproducibility_information_table = convertTables(reproducibilityInformation);
        }
    }

    private void convertMetrics(final ModelAttributes modelAttributes, final ModelDescriptor modelDescriptor) {
        // Training metrics

        if (modelAttributes.getTrainingMetrics() != null) {
            _training_metrics = convertModelMetrics(modelAttributes.getTrainingMetrics(), modelDescriptor, modelAttributes);
        }
        if (modelAttributes.getValidationMetrics() != null) {
            _validation_metrics = (ModelMetrics) convertObjects(modelAttributes.getValidationMetrics(),
                    convertModelMetrics(modelAttributes.getValidationMetrics(), modelDescriptor, modelAttributes));
        }
        if (modelAttributes.getCrossValidationMetrics() != null) {
            _cross_validation_metrics = (ModelMetrics) convertObjects(modelAttributes.getCrossValidationMetrics(),
                    convertModelMetrics(modelAttributes.getCrossValidationMetrics(), modelDescriptor, modelAttributes));
        }
        
    }

    private ModelMetrics convertModelMetrics(final MojoModelMetrics mojoMetrics, final ModelDescriptor modelDescriptor,
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
                            customMetric(mojoMetrics), binomial._mean_per_class_error,
                            convertTable(binomial._thresholds_and_metric_scores), convertTable(binomial._max_criteria_and_metric_scores),
                            convertTable(binomial._confusion_matrix), glmBinomial._nullDegreesOfFreedom, glmBinomial._residualDegreesOfFreedom,
                            glmBinomial._resDev, glmBinomial._nullDev, glmBinomial._AIC, convertTable(modelAttributesGLM._coefficients_table),
                            glmBinomial._r2, glmBinomial._description, glmBinomial._loglikelihood);
                } else {
                    return new ModelMetricsBinomialGeneric(null, null, mojoMetrics._nobs, mojoMetrics._MSE,
                            _domains[_domains.length - 1], binomial._sigma,
                            auc, binomial._logloss, convertTable(binomial._gains_lift_table),
                            customMetric(mojoMetrics), binomial._mean_per_class_error,
                            convertTable(binomial._thresholds_and_metric_scores), convertTable(binomial._max_criteria_and_metric_scores),
                            convertTable(binomial._confusion_matrix), binomial._r2, binomial._description);
                }
            case Multinomial:
                assert mojoMetrics instanceof MojoModelMetricsMultinomial;

                if (mojoMetrics instanceof MojoModelMetricsMultinomialGLM) {
                    assert modelAttributes instanceof ModelAttributesGLM;
                    final ModelAttributesGLM modelAttributesGLM = (ModelAttributesGLM) modelAttributes;
                    modelAttributesGLM.getModelParameters();
                    final MojoModelMetricsMultinomialGLM glmMultinomial = (MojoModelMetricsMultinomialGLM) mojoMetrics;
                    return new ModelMetricsMultinomialGLMGeneric(null, null, mojoMetrics._nobs, mojoMetrics._MSE,
                            _domains[_domains.length - 1], glmMultinomial._sigma,
                            convertTable(glmMultinomial._confusion_matrix), convertTable(glmMultinomial._hit_ratios),
                            glmMultinomial._logloss, customMetric(mojoMetrics),
                            glmMultinomial._mean_per_class_error, glmMultinomial._nullDegreesOfFreedom, glmMultinomial._residualDegreesOfFreedom,
                            glmMultinomial._resDev, glmMultinomial._nullDev, glmMultinomial._AIC, convertTable(modelAttributesGLM._coefficients_table),
                            glmMultinomial._r2, convertTable(glmMultinomial._multinomial_auc), convertTable(glmMultinomial._multinomial_aucpr),
                            MultinomialAucType.valueOf((String)modelAttributes.getParameterValueByName("auc_type")), glmMultinomial._description, glmMultinomial._loglikelihood);
                } else {
                    final MojoModelMetricsMultinomial multinomial = (MojoModelMetricsMultinomial) mojoMetrics;
                    return new ModelMetricsMultinomialGeneric(null, null, mojoMetrics._nobs, mojoMetrics._MSE,
                            _domains[_domains.length - 1], multinomial._sigma,
                            convertTable(multinomial._confusion_matrix), convertTable(multinomial._hit_ratios),
                            multinomial._logloss, customMetric(mojoMetrics),
                            multinomial._mean_per_class_error, multinomial._r2, convertTable(multinomial._multinomial_auc), convertTable(multinomial._multinomial_aucpr),
                            MultinomialAucType.valueOf((String)modelAttributes.getParameterValueByName("auc_type")), multinomial._description);
                }
            case Regression:
                assert mojoMetrics instanceof MojoModelMetricsRegression;

                if (mojoMetrics instanceof MojoModelMetricsRegressionGLM) {
                    assert modelAttributes instanceof ModelAttributesGLM;
                    final ModelAttributesGLM modelAttributesGLM = (ModelAttributesGLM) modelAttributes;
                    final MojoModelMetricsRegressionGLM regressionGLM = (MojoModelMetricsRegressionGLM) mojoMetrics;
                    return new ModelMetricsRegressionGLMGeneric(null, null, regressionGLM._nobs, regressionGLM._MSE,
                            regressionGLM._sigma, regressionGLM._mae, regressionGLM._root_mean_squared_log_error, regressionGLM._mean_residual_deviance,
                            customMetric(regressionGLM), regressionGLM._r2,
                            regressionGLM._nullDegreesOfFreedom, regressionGLM._residualDegreesOfFreedom, regressionGLM._resDev,
                            regressionGLM._nullDev, regressionGLM._AIC, regressionGLM._loglikelihood, convertTable(modelAttributesGLM._coefficients_table));
                } else {
                    MojoModelMetricsRegression metricsRegression = (MojoModelMetricsRegression) mojoMetrics;

                    return new ModelMetricsRegressionGeneric(null, null, metricsRegression._nobs, metricsRegression._MSE,
                            metricsRegression._sigma, metricsRegression._mae, metricsRegression._root_mean_squared_log_error, metricsRegression._mean_residual_deviance,
                            customMetric(mojoMetrics), mojoMetrics._description);
                }
            case AnomalyDetection:
                assert mojoMetrics instanceof MojoModelMetricsAnomaly;
                // There is no need to introduce new Generic alternatives to the original metric objects at the moment.
                // The total values can be simply calculated. The extra calculation time is negligible.
                MojoModelMetricsAnomaly metricsAnomaly = (MojoModelMetricsAnomaly) mojoMetrics;
                return new ModelMetricsAnomaly(null, null, customMetric(mojoMetrics),
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
                            ordinalMetrics._logloss, customMetric(ordinalMetrics),
                            ordinalMetrics._r2, ordinalMetrics._nullDegreesOfFreedom, ordinalMetrics._residualDegreesOfFreedom, ordinalMetrics._resDev,
                            ordinalMetrics._nullDev, ordinalMetrics._AIC, ordinalMetrics._loglikelihood, convertTable(modelAttributesGLM._coefficients_table),
                            convertTable(ordinalMetrics._hit_ratio_table), ordinalMetrics._mean_per_class_error, ordinalMetrics._description);
                } else {
                    MojoModelMetricsOrdinal ordinalMetrics = (MojoModelMetricsOrdinal) mojoMetrics;
                    return new ModelMetricsOrdinalGeneric(null, null, ordinalMetrics._nobs, ordinalMetrics._MSE,
                            ordinalMetrics._domain, ordinalMetrics._sigma, convertTable(ordinalMetrics._cm), ordinalMetrics._hit_ratios,
                            ordinalMetrics._logloss, customMetric(ordinalMetrics),
                            convertTable(ordinalMetrics._hit_ratio_table), ordinalMetrics._mean_per_class_error, ordinalMetrics._description);
                }
            case CoxPH:
                assert mojoMetrics instanceof MojoModelMetricsRegressionCoxPH;
                MojoModelMetricsRegressionCoxPH metricsCoxPH = (MojoModelMetricsRegressionCoxPH) mojoMetrics;
                return new ModelMetricsRegressionCoxPH(null, null, metricsCoxPH._nobs, metricsCoxPH._MSE,
                        metricsCoxPH._sigma, metricsCoxPH._mae, metricsCoxPH._root_mean_squared_log_error, metricsCoxPH._mean_residual_deviance,
                        customMetric(mojoMetrics),
                        metricsCoxPH._concordance, metricsCoxPH._concordant, metricsCoxPH._discordant, metricsCoxPH._tied_y);
            case BinomialUplift:
                assert mojoMetrics instanceof MojoModelMetricsBinomialUplift;
                MojoModelMetricsBinomialUplift metricsUplift = (MojoModelMetricsBinomialUplift) mojoMetrics;
                AUUC.AUUCType auucType = AUUC.AUUCType.valueOf((String) modelAttributes.getParameterValueByName("auuc_type"));
                AUUC auuc = createAUUC(auucType, metricsUplift._thresholds_and_metric_scores, metricsUplift._auuc_table, metricsUplift._aecu_table);
                return new ModelMetricsBinomialUpliftGeneric(null, null, metricsUplift._nobs, _domains[_domains.length - 1], 
                        metricsUplift._ate, metricsUplift._att, metricsUplift._atc, metricsUplift._sigma, auuc, customMetric(metricsUplift), 
                        convertTable(metricsUplift._thresholds_and_metric_scores), convertTable(metricsUplift._auuc_table), 
                        convertTable(metricsUplift._aecu_table), metricsUplift._description);
            case Unknown:
            case Clustering:
            case AutoEncoder:
            case DimReduction:
            case WordEmbedding:
            default:
                return new ModelMetrics(null, null, mojoMetrics._nobs, mojoMetrics._MSE, mojoMetrics._description,
                        customMetric(mojoMetrics));
        }
    }

    private static CustomMetric customMetric(MojoModelMetrics mojoModelMetrics) {
        if (mojoModelMetrics._custom_metric_name == null)
            return null;
        return new CustomMetric(mojoModelMetrics._custom_metric_name, mojoModelMetrics._custom_metric_value);
    }
    
    @Override
    public double defaultThreshold() {
        return _defaultThreshold;
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

    private static TwoDimTable convertVariableImportances(final VariableImportances variableImportances) {
        if(variableImportances == null) return null;

        TwoDimTable varImps = ModelMetrics.calcVarImp(variableImportances._importances, variableImportances._variables);
        return varImps;
    }
    
    private static TwoDimTable[] convertTables(final Table[] inputTables) {
        if (inputTables == null)
            return null;
        
        TwoDimTable[] tables = new TwoDimTable[inputTables.length];
        for (int i = 0; i < inputTables.length; i++) {
            tables[i] = convertTable(inputTables[i]);
        }
        return tables;
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

    private static AUUC createAUUC(AUUC.AUUCType auucType, Table thresholds_and_metric_scores, Table auuc_table, Table aecu_table){
        int nbins = thresholds_and_metric_scores.rows();
        double[] ths = new double[nbins];
        long[] freq = new long[nbins];
        AUUC.AUUCType[] auucTypes = AUUC.AUUCType.values();
        double[][] uplift = new double[auucTypes.length][nbins];
        double[][] upliftNorm = new double[auucTypes.length][nbins];
        double[][] upliftRand = new double[auucTypes.length][nbins];
        double[] auuc = new double[auucTypes.length];
        double[] auucNorm = new double[auucTypes.length];
        double[] auucRand = new double[auucTypes.length];
        double[] aecu = new double[auucTypes.length];
        
        String[] thrHeader = thresholds_and_metric_scores.getColHeaders();
        // threshold column index
        int thrIndex = ArrayUtils.find(thrHeader, "thresholds");
        int freqIndex = ArrayUtils.find(thrHeader, "n");
        
        // uplift type indices
        int[] upliftIndices = new int[auucTypes.length];
        int[] upliftNormIndices = new int[auucTypes.length];
        int[] upliftRandIndices = new int[auucTypes.length];
        for (int i = 1; i < auucTypes.length; i++) {
            String auucTypeName  =  auucTypes[i].name(); 
            upliftIndices[i] = ArrayUtils.find(thrHeader, auucTypeName);
            upliftNormIndices[i] = ArrayUtils.find(thrHeader, auucTypeName+"_normalized");
            upliftRandIndices[i] = ArrayUtils.find(thrHeader, auucTypeName+"_random");
            // AUTO setting
            if(auucTypeName.equals(AUUC.AUUCType.nameAuto())){
                upliftIndices[0] = upliftIndices[i];
                upliftNormIndices[0] = upliftNormIndices[i];
                upliftRandIndices[0] = upliftRandIndices[i];
            }
        }
        // fill thresholds and uplift values from table
        for (int i = 0; i < thresholds_and_metric_scores.rows(); i++) {
            ths[i] = (double) thresholds_and_metric_scores.getCell(thrIndex, i);
            freq[i] = (long) thresholds_and_metric_scores.getCell(freqIndex, i);
            for (int j = 0; j < auucTypes.length; j++) {
                uplift[j][i] = (double) thresholds_and_metric_scores.getCell(upliftIndices[j], i);
                upliftNorm[j][i] = (double) thresholds_and_metric_scores.getCell(upliftNormIndices[j], i);
                upliftRand[j][i] = (double) thresholds_and_metric_scores.getCell(upliftRandIndices[j], i);
            }
        }
        // fill auuc values and aecu values
        String[] auucHeader = auuc_table.getColHeaders();
        String[] aecuHeader = aecu_table.getColHeaders();
        for (int i = 1; i < auucTypes.length; i++) {
            AUUC.AUUCType type = auucTypes[i];
            String auucTypeName = type.name();
            int colIndex = ArrayUtils.find(auucHeader, auucTypeName);
            auuc[i] = (double) auuc_table.getCell(colIndex, 0);
            auucNorm[i] = (double) auuc_table.getCell(colIndex, 1);
            auucRand[i] = (double) auuc_table.getCell(colIndex, 2);
            colIndex = ArrayUtils.find(aecuHeader, auucTypeName);
            aecu[i] = (double) aecu_table.getCell(colIndex, 0);
            if(auucTypeName.equals(AUUC.AUUCType.nameAuto())){
                auuc[0] = auuc[i];
                auucNorm[0] = auucNorm[i];
                auucRand[0] = auucRand[i];
                aecu[0] = aecu[i];
            }
        }
        return new AUUC(ths, freq, auuc, auucNorm, auucRand, aecu, auucType, uplift, upliftNorm, upliftRand);
    }

    @Override
    public boolean hasTreatment() {
        return super.hasTreatment();
    }
}
