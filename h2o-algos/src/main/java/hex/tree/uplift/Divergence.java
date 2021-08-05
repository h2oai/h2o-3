package hex.tree.uplift;

import water.Iced;

/**
 * Divergence class used to calculate gain to split the node in Uplift trees algorithms. 
 * Currently only UpliftRandomForest uses this class.
 * Source: https://link.springer.com/content/pdf/10.1007/s10115-011-0434-0.pdf page 308
 * 
 */
public abstract class Divergence extends Iced<Divergence> {

    /**
     * Calculate distance divergence metric between two probabilities.
     * @param prCT1
     * @param prCT0
     * @return distance divergence metric
     */
    public abstract double metric(double prCT1, double prCT0);

    /**
     * Calculate distance metric between two probabilities in the node.
     * @param prCT1 probability of treatment group
     * @param prCT0 probability of control group
     * @return distance divergence metric in the node
     */
    public double node(double prCT1, double prCT0) {
        return metric(prCT1, prCT0) + metric(1 - prCT1, 1 - prCT0);
    }

    /**
     * Calculate gain after split
     * @param prL probability of response in left node
     * @param prLY1CT1 probability of response = 1 in treatment group in left node
     * @param prLY1CT0 probability of response = 1 in control group in left node
     * @param prR probability of response in right node
     * @param prRY1CT1 probability of response = 1 in treatment group in right node
     * @param prRY1CT0 probability of response = 1 in control group in right node
     * @return gain after split
     */
    public double split( double prL, double prLY1CT1, double prLY1CT0, 
                         double prR, double prRY1CT1, double prRY1CT0) {
        double klL = node(prLY1CT1, prLY1CT0);
        double klR = node(prRY1CT1, prRY1CT0);
        return prL * klL + prR * klR;
    }

    /**
     * Calculate overall gain as divergence between split gain and node gain.
     * @param prY1CT1 probability of response = 1 in treatment group before split
     * @param prY1CT0 probability of response = 1 in control group before 
     * @param prL probability of response in left node
     * @param prLY1CT1 probability of response = 1 in treatment group in left node
     * @param prLY1CT0 probability of response = 1 in control group in left node
     * @param prR probability of response in right node
     * @param prRY1CT1 probability of response = 1 in treatment group in right node
     * @param prRY1CT0 probability of response = 1 in control group in right node
     * @return overall gain
     */
    public double gain(double prY1CT1, double prY1CT0, double prL, double prLY1CT1, double prLY1CT0, 
                       double prR, double prRY1CT1, double prRY1CT0) {
        return split(prL, prLY1CT1, prLY1CT0, prR, prRY1CT1, prRY1CT0) - node(prY1CT1, prY1CT0);
    }

    /**
     * Calculate normalization factor to normalize gain.
     * @param prCT1 probability of treatment group
     * @param prCT0 probability of control group
     * @param prLCT1 probability of treatment group in left node
     * @param prLCT0 probability of control group in left node
     * @return normalization factor
     */
    public abstract double norm(double prCT1, double prCT0, double prLCT1, double prLCT0);

    /**
     * Calculate normalized gain as result value to select best split.
     * @param prY1CT1 probability of response = 1 in treatment group before split
     * @param prY1CT0 probability of response = 1 in control group before 
     * @param prL probability of response in left node
     * @param prLY1CT1 probability of response = 1 in treatment group in left node
     * @param prLY1CT0 probability of response = 1 in control group in left node
     * @param prR probability of response in right node
     * @param prRY1CT1 probability of response = 1 in treatment group in right node
     * @param prRY1CT0 probability of response = 1 in control group in right node
     * @param prCT1 probability of treatment group
     * @param prCT0 probability of control group
     * @param prLCT1 probability of treatment group in left node
     * @param prLCT0 probability of control group in left node
     * @return normalized gain
     */
    public double value(double prY1CT1, double prY1CT0, double prL, double prLY1CT1, double prLY1CT0, 
                        double prR, double prRY1CT1, double prRY1CT0, double prCT1, double prCT0, 
                        double prLCT1, double prLCT0) {
        return gain(prY1CT1, prY1CT0, prL,  prLY1CT1, prLY1CT0, prR, prRY1CT1, prRY1CT0) / 
                norm(prCT1, prCT0, prLCT1, prLCT0);
    }
}
