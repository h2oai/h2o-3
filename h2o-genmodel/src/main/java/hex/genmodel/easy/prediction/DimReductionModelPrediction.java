package hex.genmodel.easy.prediction;

/**
 * TODO
 */
public class DimReductionModelPrediction extends AbstractPrediction {
    public double[] dimensions; // contains the X factor/coefficient
    /**
     * This field is only used for GLRM and not for PCA.  Reconstructed data, the array has same length as the
     * original input. The user can use the original input and reconstructed output to easily calculate eg. the
     * reconstruction error.  Note that all values are either doubles or integers.  Users need to convert
     * the enum columns from the integer columns if necessary.
     */
    public double[] reconstructed;

}
