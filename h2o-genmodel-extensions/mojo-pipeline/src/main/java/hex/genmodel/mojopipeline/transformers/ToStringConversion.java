package hex.genmodel.mojopipeline.transformers;

import ai.h2o.mojos.runtime.api.backend.ReaderBackend;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.transforms.MojoTransform;
import ai.h2o.mojos.runtime.transforms.MojoTransformBuilderFactory;
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;

import java.util.HashMap;
import java.util.Map;

public class ToStringConversion extends MojoTransform {

    ToStringConversionFunction _function;

    ToStringConversion(int[] iindices, int[] oindices, ToStringConversionFunction function) {
        super(iindices, oindices);
        _function = function;
    }

    @Override
    public void transform(MojoFrame frame) {
        Object input = frame.getColumnData(iindices[0]);
        String[] o = (String[]) frame.getColumnData(oindices[0]);
        if (input instanceof String[]){
            String[] a = (String[]) input;
            for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
                o[i] = a[i];
            }
        } else {
            double[] a = (double[]) input;
            for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
                if (Double.isNaN(a[i])) {
                    o[i] = null;
                } else {
                    o[i] = _function.call(a[i]);
                }
            }
        }
    }

   interface ToStringConversionFunction {
        String call(double value);
    }

    public static class Factory implements MojoTransformBuilderFactory {
        
        private static final ToStringConversionFunction _defaultConversionFunction = new ToStringConversionFunction() {
            @Override
            public String call(double value) { return ((Double)value).toString();}
        };
        
        private static final HashMap<String,ToStringConversionFunction> _supportedFunctions = 
            new HashMap<String,ToStringConversionFunction>() {{
                put("as.factor", _defaultConversionFunction);
                put("as.character", _defaultConversionFunction);
        }};

        public static final String TRANSFORMER_ID = "hex.genmodel.mojopipeline.transformers.ToStringConversion";
        
        public static ToStringConversionFunction getFunction(String functionName) {
            final ToStringConversionFunction function = _supportedFunctions.get(functionName);
            if (function == null) {
                throw new UnsupportedOperationException(
                    String.format("The function '%s' is not supported conversion to string.", functionName));
            }
            return function;
        }
        
        public static boolean functionExists(String functionName) {
            return _supportedFunctions.containsKey(functionName);
        }

        @Override
        public String transformerName() {
            return TRANSFORMER_ID;
        }

        @Override
        public MojoTransform createBuilder(MojoFrameMeta meta,
                                           int[] iindcies, int[] oindices,
                                           Map<String, Object> params,
                                           ReaderBackend backend) {
            final String functionName = (String) params.get("function");
            final ToStringConversionFunction function = Factory.getFunction(functionName);
            return new ToStringConversion(iindcies, oindices, function);
        }
    }
}
