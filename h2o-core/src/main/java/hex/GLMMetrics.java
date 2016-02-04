package hex;

/**
 * Created by tomasnykodym on 1/5/16.
 */
public interface GLMMetrics {
    double residualDeviance();
    double nullDeviance();
    long residualDegreesOfFreedom();
    long nullDegreesOfFreedom();
}
