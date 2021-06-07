package hex;

import water.fvec.Frame;

/**
 * Implementors of this interface have Friedman & Popescu's H calculation implemented.
 */
public interface FriedmanPopescusHCollector {
    
    double getFriedmanPopescusH(Frame frame, String[] vars);
}
