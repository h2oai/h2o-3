package hex;

/**
 * Created by tomasnykodym on 1/5/16.
 */
public interface GLMMetrics {
    double residual_deviance(); //naming is pythonic because its user-facing via grid search sort criterion
    double null_deviance();
    long residual_degrees_of_freedom();
    long null_degrees_of_freedom();
}
