package hex;
import water.util.TwoDimTable;

/**
 * Implementors of this interface have feature interactions calculation implemented.
 */
public interface FeatureInteractionsCollector {
    
    TwoDimTable[][] getFeatureInteractionsTable(int maxInteractionDepth, int maxTreeDepth, int maxDeepening);
}
