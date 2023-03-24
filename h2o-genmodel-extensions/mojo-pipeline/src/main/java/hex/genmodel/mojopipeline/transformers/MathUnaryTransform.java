package hex.genmodel.mojopipeline.transformers;

import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;
import ai.h2o.mojos.runtime.api.backend.ReaderBackend;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.transforms.MojoTransform;
import ai.h2o.mojos.runtime.transforms.MojoTransformBuilderFactory;

import java.util.HashMap;
import java.util.Map;

public class MathUnaryTransform extends MojoTransform {

    MathUnaryFunction _function;

    MathUnaryTransform(int[] iindices, int[] oindices, MathUnaryFunction function) {
        super(iindices, oindices);
        _function = function;
    }

    @Override
    public void transform(MojoFrame frame) {
        double[] a = (double[]) frame.getColumnData(iindices[0]);
        double[] o = (double[]) frame.getColumnData(oindices[0]);
        for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
            o[i] = _function.call(a[i]);
        }
    }

   interface MathUnaryFunction {
        void initialize(Map<String, Object> params);
        double call(double value);
    }

    public static class Factory implements MojoTransformBuilderFactory {
        
        private static final HashMap<String,MathUnaryFunction> _supportedFunctions = new HashMap<String,MathUnaryFunction>() {{
            put("abs", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.abs(value); }
            });
            put("acos", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.acos(value); }
            });
            put("acosh", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return FastMath.acosh(value); }
            });
            put("asin", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.asin(value); }
            });
            put("asinh", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return FastMath.asinh(value); }
            });
            put("atan", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.atan(value); }
            });
            put("atanh", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return FastMath.atanh(value); }
            });
            put("ceiling", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.ceil(value); }
            });
            put("cos", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.cos(value); }
            });
            put("cosh", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.cosh(value); }
            });
            put("cospi", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.cos(Math.PI * value); }
            });
            put("digamma", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Double.isNaN(value) ? Double.NaN : Gamma.digamma(value); }
            });
            put("exp", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.exp(value); }
            });
            put("expm1", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.expm1(value); }
            });
            put("floor", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.floor(value); }
            });
            put("gamma", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Gamma.gamma(value); }
            });
            put("lgamma", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Gamma.logGamma(value); }
            });
            put("log", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.log(value); }
            });
            put("log1p", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.log1p(value); }
            });
            put("log2", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.log(value) / Math.log(2); }
            });
            put("log10", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.log10(value); }
            });
            put("none", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return value; }
            });
            put("not", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Double.isNaN(value) ? Double.NaN : value == 0 ? 1 : 0; }
            });
            put("sign", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.signum(value); }
            });
            put("sin", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.sin(value); }
            });
            put("sinh", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.sinh(value); }
            });
            put("sinpi", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.sin(Math.PI * value); }
            });
            put("sqrt", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.sqrt(value); }
            });
            put("tan", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.tan(value); }
            });
            put("tanh", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.tanh(value); }
            });
            put("tanpi", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Math.tan(Math.PI * value); }
            });
            put("trigamma", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return Double.isNaN(value) ? Double.NaN : Gamma.trigamma(value); }
            });
            put("trunc", new MathUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(double value) { return value >= 0 ? Math.floor(value) : Math.ceil(value); }
            });
            put("round", new MathUnaryFunction() {
                double _digits = Double.NaN;
                double _powerOf10 = Double.NaN;
                
                @Override
                public void initialize(Map<String, Object> params) {
                    Object digitsObj = params.get("rightArg");
                    if (digitsObj == null) {
                        throw new IllegalArgumentException("The 'digits' param is not passed to 'round' function!");
                    }
                    _digits = (Double)digitsObj;
                    if ((int) _digits != _digits) _digits = Math.round(_digits);
                    _powerOf10 = (int) Math.pow(10, (int) _digits);
                }
                @Override
                public double call(double x) {
                    if (Double.isNaN(x)) return x;
                    double sgn = x < 0 ? -1 : 1;
                    x = Math.abs(x);
                    return sgn * (_digits == 0
                            // go to the even digit
                            ? (x % 1 > 0.5 || (x % 1 == 0.5 && !(Math.floor(x) % 2 == 0)))
                            ? Math.ceil(x)
                            : Math.floor(x)
                            : Math.floor(x * _powerOf10 + 0.5) / _powerOf10);
                }
            });
            put("signif", new MathUnaryFunction() {
                double _digits = Double.NaN;

                @Override
                public void initialize(Map<String, Object> params) {
                    Object digitsObj = params.get("rightArg");
                    if (digitsObj == null) {
                        throw new IllegalArgumentException("The 'digits' param is not passed to 'signif' function!");
                    }
                    _digits = (Double)digitsObj;
                    if (_digits < 1) _digits = 1; //mimic R's base::signif
                    if ((int) _digits != _digits) _digits = Math.round(_digits);
                }
                @Override
                public double call(double x) {
                    if (Double.isNaN(x)) return x;
                    java.math.BigDecimal bd = new java.math.BigDecimal(x);
                    bd = bd.round(new java.math.MathContext((int) _digits, java.math.RoundingMode.HALF_EVEN));
                    return bd.doubleValue();
                }
            });
        }};

        public static final String TRANSFORMER_ID = "hex.genmodel.mojopipeline.transformers.MathUnaryTransform";
        
        public static MathUnaryFunction getFunction(String functionName) {
            final MathUnaryFunction function = _supportedFunctions.get(functionName);
            if (function == null) {
                throw new UnsupportedOperationException(
                        String.format("The function '%s' is not supported unary math transformation.", functionName));
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
            final MathUnaryFunction function = Factory.getFunction(functionName);
            function.initialize(params);
            return new MathUnaryTransform(iindcies, oindices, function);
        }
    }
}
