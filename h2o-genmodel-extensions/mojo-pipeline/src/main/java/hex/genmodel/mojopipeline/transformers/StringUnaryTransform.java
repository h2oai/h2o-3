package hex.genmodel.mojopipeline.transformers;

import ai.h2o.mojos.runtime.api.backend.ReaderBackend;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.transforms.MojoTransform;
import ai.h2o.mojos.runtime.transforms.MojoTransformBuilderFactory;
import hex.genmodel.mojopipeline.parsing.ParameterParser;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class StringUnaryTransform extends MojoTransform {

    StringUnaryFunction _function;

    StringUnaryTransform(int[] iindices, int[] oindices, StringUnaryFunction function) {
        super(iindices, oindices);
        _function = function;
    }

    @Override
    public void transform(MojoFrame frame) {
        String[] a = (String[]) frame.getColumnData(iindices[0]);
        String[] o = (String[]) frame.getColumnData(oindices[0]);
        for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
            o[i] = a[i] == null ? null : _function.call(a[i]);
        }
    }

   interface StringUnaryFunction {
        void initialize(Map<String, Object> params);
        String call(String value);
    }

    public static class Factory implements MojoTransformBuilderFactory {
        private static final HashMap<String,StringUnaryFunction> _supportedFunctions = new HashMap<String,StringUnaryFunction>() {{
            put("lstrip", new StringUnaryFunction() {
                private String _set = null;
                @Override
                public void initialize(Map<String, Object> params) {
                    Object setObj = params.get("set");
                    if (setObj == null) {
                        throw new IllegalArgumentException("The 'set' param is not passed to 'lstrip' function!");
                    }
                    _set = (String)setObj;
                }
                @Override
                public String call(String value) {
                    return StringUtils.stripStart(value, _set);
                }
            });
            put("rstrip", new StringUnaryFunction() {
                private String _set = null;
                @Override
                public void initialize(Map<String, Object> params) {
                    Object setObj = params.get("set");
                    if (setObj == null) {
                        throw new IllegalArgumentException("The 'set' param is not passed to 'rstrip' function!");
                    }
                    _set = (String)setObj;
                }
                @Override
                public String call(String value) {
                    return StringUtils.stripEnd(value, _set);
                }
            });
            put("replaceall", new StringUnaryFunction() {
                Pattern _pattern =  null;
                String _replacement = null;
                Boolean _ignoreCase = null;
                
                @Override
                public void initialize(Map<String, Object> params) {
                    Object patternObj = params.get("pattern");
                    if (patternObj == null) {
                        throw new IllegalArgumentException("The 'pattern' param is not passed to 'replaceall' function!");
                    }
                    String stringPattern = (String)patternObj;
                    _pattern = Pattern.compile(stringPattern);
                    
                    Object replacementObj = params.get("replacement");
                    if (replacementObj == null) {
                        throw new IllegalArgumentException("The 'replacement' param is not passed to 'replaceall' function!");
                    }
                    _replacement = (String)replacementObj;
                    
                    Object ignoreCaseObj = params.get("ignore_case");
                    if (ignoreCaseObj == null) {
                        throw new IllegalArgumentException("The 'ignore_case' param is not passed to 'replaceall' function!");
                    }
                    _ignoreCase = ParameterParser.paramValueToBoolean(ignoreCaseObj);
                }
                @Override
                public String call(String value) {
                    if (_ignoreCase)
                        return _pattern.matcher(value.toLowerCase(Locale.ENGLISH)).replaceAll(_replacement);
                    else
                        return _pattern.matcher(value).replaceAll(_replacement);
                }
            });
            put("replacefirst", new StringUnaryFunction() {
                Pattern _pattern =  null;
                String _replacement = null;
                Boolean _ignoreCase = null;

                @Override
                public void initialize(Map<String, Object> params) {
                    Object patternObj = params.get("pattern");
                    if (patternObj == null) {
                        throw new IllegalArgumentException("The 'pattern' param is not passed to 'replacefirst' function!");
                    }
                    String stringPattern = (String)patternObj;
                    _pattern = Pattern.compile(stringPattern);

                    Object replacementObj = params.get("replacement");
                    if (replacementObj == null) {
                        throw new IllegalArgumentException("The 'replacement' param is not passed to 'replacefirst' function!");
                    }
                    _replacement = (String)replacementObj;

                    Object ignoreCaseObj = params.get("ignore_case");
                    if (ignoreCaseObj == null) {
                        throw new IllegalArgumentException("The 'ignore_case' param is not passed to 'replacefirst' function!");
                    }
                    _ignoreCase = ParameterParser.paramValueToBoolean(ignoreCaseObj);
                }
                @Override
                public String call(String value) {
                    if (_ignoreCase)
                        return _pattern.matcher(value.toLowerCase(Locale.ENGLISH)).replaceFirst(_replacement);
                    else
                        return _pattern.matcher(value).replaceFirst(_replacement);
                }
            });
            put("substring", new StringUnaryFunction() {
                private int _startIndex = 0;
                private int _endIndex = Integer.MAX_VALUE;
                
                @Override
                public void initialize(Map<String, Object> params) {
                    Object startIndexObj = params.get("startIndex");
                    if (startIndexObj != null) {
                        _startIndex = ((Double) startIndexObj).intValue();
                        if (_startIndex < 0) _startIndex = 0;
                    }
                    Object endIndexObj = params.get("endIndex");
                    if (endIndexObj != null) {
                        _endIndex = ((Double) endIndexObj).intValue();
                    }
                }
                @Override
                public String call(String value) {
                    return value.substring(
                        _startIndex < value.length() ? _startIndex : value.length(),
                        _endIndex < value.length() ? _endIndex : value.length());
                }
            });
            put("tolower", new StringUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public String call(String value) { return value.toLowerCase(Locale.ENGLISH); }
            });
            put("toupper", new StringUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public String call(String value) { return value.toUpperCase(Locale.ENGLISH); }
            });
            put("trim", new StringUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public String call(String value) { return value.trim(); }
            });
        }};

        public static final String TRANSFORMER_ID = "hex.genmodel.mojopipeline.transformers.StringUnaryFunction";
        
        public static StringUnaryFunction getFunction(String functionName) {
            final StringUnaryFunction function = _supportedFunctions.get(functionName);
            if (function == null) {
                throw new UnsupportedOperationException(
                    String.format("The function '%s' is not supported unary string transformation.", functionName));
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
            final StringUnaryFunction function = Factory.getFunction(functionName);
            function.initialize(params);
            return new StringUnaryTransform(iindcies, oindices, function);
        }
    }
}
