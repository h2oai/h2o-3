package hex;

import water.fvec.Frame;

/**
 * Implementors of this interface have fitRules calculation implemented.
 */
public interface FitRulesCollector {
    
    Frame fitRules(Frame frame, String[] ruleIds);
}
