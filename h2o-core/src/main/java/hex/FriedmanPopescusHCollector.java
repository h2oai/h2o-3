package hex;

import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Implementors of this interface have Friedman & Popescu's H calculation implemented.
 */
public interface FriedmanPopescusHCollector {
    
    double getFriedmanPopescusH(Frame frame, String[] vars);
    
    /**
     * Validates the input parameters for Friedman Popescu's H statistic calculation.
     * 
     * @param frame The input frame
     * @param vars The variables to calculate H statistic for
     * @throws IllegalArgumentException if validation fails
     */
    default void validateFriedmanPopescusHInput(Frame frame, String[] vars) {
        // Validate vars parameter
        if (vars == null || vars.length == 0) {
            throw new IllegalArgumentException(
                    "Calculating H statistics error: 'vars' parameter cannot be null or empty. " +
                    "Please specify at least one variable.");
        }

        // Validate that all specified columns exist in the frame and are numeric
        for (String varName : vars) {
            if (varName == null) {
                throw new IllegalArgumentException(
                        "Calculating H statistics error: 'vars' parameter contains a null value.");
            }
            Vec col = frame.vec(varName);
            if (col == null) {
                throw new IllegalArgumentException(
                        "Calculating H statistics error: column '" + varName + "' does not exist in the frame.");
            }
            if (!col.isNumeric()) {
                throw new IllegalArgumentException(
                        "Calculating H statistics error: column '" + varName + "' is not numeric. " +
                        "H statistics can only be calculated for numeric variables.");
            }
            if (col.isBad() || col.isConst()) {
                throw new IllegalArgumentException(
                        "Calculating H statistics error: column '" + varName + "' contains only missing or constant values.");
            }
        }
    }
}
