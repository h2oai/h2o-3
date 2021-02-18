package hex.tree.uplift;

import water.Iced;

public abstract class Divergence extends Iced<Divergence> {

    /**
     * 
     * @param prCT1
     * @param prCT0
     * @return
     */
    public abstract double node(
        double prCT1,
        double prCT0
    );

    /**
     * 
     * @param prL
     * @param prLY1CT1
     * @param prLY1CT0
     * @param prR
     * @param prRY1CT1
     * @param prRY1CT0
     * @return
     */
    public double split(
        double prL, double prLY1CT1, double prLY1CT0,
        double prR, double prRY1CT1, double prRY1CT0
    ) {
        double klL = node(prLY1CT1, prLY1CT0);
        double klR = node(prRY1CT1, prRY1CT0);
        return prL * klL + prR * klR;
    }

    /**
     * 
     * @param prY1CT1
     * @param prY1CT0
     * @param prL
     * @param prLY1CT1
     * @param prLY1CT0
     * @param prR
     * @param prRY1CT1
     * @param prRY1CT0
     * @return
     */
    public double gain(
        double prY1CT1, double prY1CT0, 
        double prL, double prLY1CT1, double prLY1CT0,
        double prR, double prRY1CT1, double prRY1CT0
    ) {
        return split(prL, prLY1CT1, prLY1CT0, prR, prRY1CT1, prRY1CT0) - node(prY1CT1, prY1CT0);
    }

    /**
     * 
     * @param prCT1
     * @param prCT0
     * @param prLCT1
     * @param prLCT0
     * @return
     */
    public abstract double norm(
        double prCT1, double prCT0,
        double prLCT1, double prLCT0
    );

    /**
     * Calculate normalized gain
     * @param prY1CT1 probability of response = 1 in treatment group before split
     * @param prY1CT0 probability of response = 1 in control group before 
     * @param prL probability of response in left node
     * @param prLY1CT1 probability of response = 1 in treatment group in left node
     * @param prLY1CT0 probability of response = 1 in control group in left node
     * @param prR probability of response in right node
     * @param prRY1CT1 probability of response = 1 in treatment group in right node
     * @param prRY1CT0 probability of response = 1 in control group in right node
     * @param prCT1 probability in treatment group
     * @param prCT0 probability in control group
     * @param prLCT1 probability in treatment group in left node
     * @param prLCT0 probability in control group in left node
     * @return
     */
    public double value(
        double prY1CT1, double prY1CT0,
        double prL, double prLY1CT1, double prLY1CT0,
        double prR, double prRY1CT1, double prRY1CT0,
        double prCT1, double prCT0,
        double prLCT1, double prLCT0
    ) {
        return
            gain(prY1CT1, prY1CT0, prL,  prLY1CT1, prLY1CT0, prR, prRY1CT1, prRY1CT0)
                /
            norm(prCT1, prCT0, prLCT1, prLCT0);
    }
}
