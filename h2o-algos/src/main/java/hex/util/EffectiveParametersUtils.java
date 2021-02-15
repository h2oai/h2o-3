package hex.util;

import hex.Model;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.SharedTreeModel;
import hex.tree.uplift.UpliftDRFModel;

public class EffectiveParametersUtils {
    
    public static void initFoldAssignment(
        Model.Parameters params
    ) {
        if (params._fold_assignment == Model.Parameters.FoldAssignmentScheme.AUTO) {
            if (params._nfolds > 0 && params._fold_column == null) {
                params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Random;
            } else {
                params._fold_assignment = null;
            }
        }
    }
    
    public static void initHistogramType(
            SharedTreeModel.SharedTreeParameters params
    ) {
        if (params._histogram_type == SharedTreeModel.SharedTreeParameters.HistogramType.AUTO) {
            params._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive;
        }
    }
    
    public static void initStoppingMetric(
            Model.Parameters params,
            boolean isClassifier
    ) {
        if (params._stopping_metric == ScoreKeeper.StoppingMetric.AUTO) {
            if (params._stopping_rounds == 0) {
                params._stopping_metric = null;
            } else {
                if (isClassifier) {
                    params._stopping_metric = ScoreKeeper.StoppingMetric.logloss;
                } else {
                    params._stopping_metric = ScoreKeeper.StoppingMetric.deviance;
                }
            }
        }
    }
    
    public static void initDistribution(
            Model.Parameters params,
            int nclasses
    ) {
        if (params._distribution == DistributionFamily.AUTO) {
            if (nclasses == 1) {
                params._distribution = DistributionFamily.gaussian;}
            if (nclasses == 2) {
                params._distribution = DistributionFamily.bernoulli;}
            if (nclasses >= 3) {
                params._distribution = DistributionFamily.multinomial;}
        }
    }

    public static void initCategoricalEncoding(
            Model.Parameters params,
            Model.Parameters.CategoricalEncodingScheme scheme
    ) {
        if (params._categorical_encoding == Model.Parameters.CategoricalEncodingScheme.AUTO) {
            params._categorical_encoding = scheme;
        }
    }
    
    public static void initUpliftMetric(UpliftDRFModel.UpliftDRFParameters params
    ) {
        if (params._uplift_metric == UpliftDRFModel.UpliftDRFParameters.UpliftMetricType.AUTO) {
            params._uplift_metric = UpliftDRFModel.UpliftDRFParameters.UpliftMetricType.KL;
        }
    }
}
