package hex.tree.sdt;

import hex.*;
import org.apache.log4j.Logger;
import water.*;

public class SDTModel extends Model<SDTModel, SDTModel.SDTParameters, SDTModel.SDTOutput> {

    private static final Logger LOG = Logger.getLogger(SDTModel.class);

    
    public SDTModel(Key<SDTModel> selfKey, SDTModel.SDTParameters parms,
                    SDTModel.SDTOutput output) {
        super(selfKey, parms, output);
    }
    
    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        switch(_output.getModelCategory()) {
            case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
            case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain, _parms._auc_type);
            case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
            default: throw H2O.unimpl();
        }
    }

    @Override
    protected double[] score0(double[] data, double[] preds) {
        assert _output.treeKey != null : "Output has no tree, check if tree is properly set to the output.";
        // compute score for given point
        CompressedSDT tree = DKV.getGet(_output.treeKey);
        int predictionValue = tree.predictRowStartingFromNode(data, 0);
        return new double[]{predictionValue};
    }

    public static class SDTOutput extends Model.Output {
        // all parameters of tree to be able to build it (?)
        public int depth;
        
        public Key<CompressedSDT> treeKey;

        public SDTOutput(SDT sdt) {
            super(sdt);
            depth = sdt._parms.depth;
        }
        
    }
    
    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        Keyed.remove(_output.treeKey, fs, true);
        return super.remove_impl(fs, cascade);
    }

    @Override
    protected AutoBuffer writeAll_impl(AutoBuffer ab) {
        ab.putKey(_output.treeKey);
        return super.writeAll_impl(ab);
    }

    @Override
    protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
        ab.getKey(_output.treeKey, fs);
        return super.readAll_impl(ab,fs);
    }

    public static class SDTParameters extends Model.Parameters {

        /**
         * Depth (max depth) of the tree
         */
        public int depth;
        
        
        public SDTParameters() {
            super();
            depth = 100;
        }
        
        @Override
        public String algoName() {
            return "SDT";
        }

        @Override
        public String fullName() {
            return "Single Decision Tree";
        }

        @Override
        public String javaName() {
            return SDTModel.class.getName();
        }

        @Override
        public long progressUnits() {
            return 0;
        } // todo - ??
    }
}
