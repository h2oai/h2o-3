package hex.genmodel.mojopipeline.transformers;

import ai.h2o.mojos.runtime.api.backend.ReaderBackend;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.transforms.MojoTransform;
import ai.h2o.mojos.runtime.transforms.MojoTransformBuilderFactory;
import water.util.comparison.string.StringComparatorFactory;
import water.util.comparison.string.StringComparator;

import java.util.HashMap;
import java.util.Map;

public class StringPropertiesBinaryTransform extends MojoTransform {

    StringPropertiesBinaryFunction _function;
    boolean _isLeftCol;
    boolean _isRightCol;
    String _constValue;

    StringPropertiesBinaryTransform(
            int[] iindices, 
            int[] oindices,
            StringPropertiesBinaryFunction function, 
            boolean isLeftCol, 
            boolean isRightCol, 
            String constValue) {
        super(iindices, oindices);
        _function = function;
        _isLeftCol = isLeftCol;
        _isRightCol = isRightCol;
        _constValue = constValue;
    }

    @Override
    public void transform(MojoFrame frame) {
        if (!_isLeftCol) {
            String[] values = (String[]) frame.getColumnData(iindices[0]);
            double[] o = (double[]) frame.getColumnData(oindices[0]);
            for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
                o[i] = _function.call(_constValue, values[i]);
            }
        } else if (!_isRightCol) {
            String[] values = (String[]) frame.getColumnData(iindices[0]);
            double[] o = (double[]) frame.getColumnData(oindices[0]);
            for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
                o[i] = _function.call(values[i], _constValue);
            }
        } else {
            String[] left = (String[]) frame.getColumnData(iindices[0]);
            String[] right = (String[]) frame.getColumnData(iindices[1]);
            double[] o = (double[]) frame.getColumnData(oindices[0]);
            for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
                o[i] = _function.call(left[i], right[i]);
            }
        }
    }

   interface StringPropertiesBinaryFunction {
        
        void initialize(Map<String, Object> params);
        
        double call(String left, String right);
    }

    public static class Factory implements MojoTransformBuilderFactory {
        
        private static final HashMap<String,StringPropertiesBinaryFunction> _supportedFunctions = new HashMap<String,StringPropertiesBinaryFunction>() {{
            put("strDistance", new StringPropertiesBinaryFunction() {
                StringComparator _comparator = null;
                
                boolean _compareEmpty = false;
                
                @Override
                public void initialize(Map<String, Object> params) {
                    Object measureObj = params.get("measure");
                    if (measureObj == null) {
                        throw new IllegalArgumentException("The 'measure' param is not passed to 'strDistance' function!");
                    }
                    String measure = (String) measureObj;
                    _comparator = StringComparatorFactory.makeComparator(measure);

                    Object compareEmptyObj = params.get("compare_empty");
                    if (compareEmptyObj == null) {
                        throw new IllegalArgumentException("The 'compare_empty' param is not passed to 'strDistance' function!");
                    }
                    _compareEmpty = Boolean.parseBoolean((String) compareEmptyObj);
                }

                @Override
                public double call(String left, String right) {
                    if (!_compareEmpty && (left.isEmpty() || right.isEmpty())) {
                        return Double.NaN;
                    } else {
                        return _comparator.compare(left, right);
                    }
                }
            });
        }};

        public static final String TRANSFORMER_ID = "hex.genmodel.mojopipeline.transformers.StringPropertiesBinaryTransform";
        
        public static StringPropertiesBinaryFunction getFunction(String functionName) {
            final StringPropertiesBinaryFunction function = _supportedFunctions.get(functionName);
            if (function == null) {
                throw new UnsupportedOperationException(
                        String.format("The function '%s' is not supported binary string properties transformation.", functionName));
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
            final Boolean isLeftCol = (Boolean) params.get("isLeftCol");
            final Boolean isRightCol = (Boolean) params.get("isRightCol");
            String constValue = null;
            if (!isLeftCol || !isRightCol) {
                constValue = (String) params.get("constValue");
            }
            final StringPropertiesBinaryFunction function = Factory.getFunction(functionName);
            function.initialize(params);
            return new StringPropertiesBinaryTransform(iindcies, oindices, function, isLeftCol, isRightCol, constValue);
        }
    }
}
