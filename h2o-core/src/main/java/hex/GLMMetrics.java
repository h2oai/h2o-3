package hex;

import water.util.ComparisonUtils;

/**
 * Created by tomasnykodym on 1/5/16.
 */
public interface GLMMetrics {
    double residual_deviance(); //naming is pythonic because its user-facing via grid search sort criterion
    double null_deviance();
    long residual_degrees_of_freedom();
    long null_degrees_of_freedom();
    
    static ComparisonUtils.AccumulatedComparisonResult compareMetricsUpToTolerance(GLMMetrics first, GLMMetrics second, double proportionalTolerance) {
        ComparisonUtils.AccumulatedComparisonResult result = new ComparisonUtils.AccumulatedComparisonResult();
        result.compareValuesUpToTolerance("residual_deviance" ,first.residual_deviance(), second.residual_deviance(), proportionalTolerance);
        result.compareValuesUpToTolerance("null_deviance", first.null_deviance(), second.null_deviance(), proportionalTolerance);
        result.compareValuesUpToTolerance("residual_degrees_of_freedom", first.residual_degrees_of_freedom(), second.residual_degrees_of_freedom(), proportionalTolerance);
        result.compareValuesUpToTolerance("null_degrees_of_freedom", first.null_degrees_of_freedom(), second.null_degrees_of_freedom(), proportionalTolerance);
        return result;
    }
}
