package hex.genmodel.mojopipeline.transformers;

import ai.h2o.mojos.runtime.api.backend.ReaderBackend;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.transforms.MojoTransform;
import ai.h2o.mojos.runtime.transforms.MojoTransformBuilderFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class StringPropertiesUnaryTransform extends MojoTransform {

    StringPropertiesUnaryFunction _function;

    StringPropertiesUnaryTransform(int[] iindices, int[] oindices, StringPropertiesUnaryFunction function) {
        super(iindices, oindices);
        _function = function;
    }

    @Override
    public void transform(MojoFrame frame) {
        String[] a = (String[]) frame.getColumnData(iindices[0]);
        double[] o = (double[]) frame.getColumnData(oindices[0]);
        for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
            o[i] = a[i] == null ? null : _function.call(a[i]);
        }
    }

   interface StringPropertiesUnaryFunction {
        void initialize(Map<String, Object> params);
        double call(String value);
    }

    public static class Factory implements MojoTransformBuilderFactory {
        
        private static final HashMap<String, StringPropertiesUnaryFunction> _supportedFunctions = new HashMap<String,StringPropertiesUnaryFunction>() {{
            put("countmatches", new StringPropertiesUnaryFunction() {
                String[] _pattern =  null;

                @Override
                public void initialize(Map<String, Object> params) {
                    Object patternObj = params.get("pattern");
                    if (patternObj == null) {
                        throw new IllegalArgumentException("The 'pattern' param is not passed to 'countmatches' function!");
                    }
                    if (patternObj instanceof String) {
                        _pattern = ((String)patternObj).split("`````");
                    } else {
                        throw new IllegalArgumentException(
                            String.format(
                                "The type '%s' of 'pattern' param is not supported.",
                                patternObj.getClass().getName()));
                    }
                }
                @Override
                public double call(String value) {
                    int count = 0;
                    for (String word : _pattern) {
                        count += StringUtils.countMatches(value, word);
                    }
                    return count;
                }
            });
            put("num_valid_substrings", new StringPropertiesUnaryFunction() {
                HashSet<String> _words =  null;

                @Override
                public void initialize(Map<String, Object> params) {
                    Object wordsObj = params.get("words");
                    if (wordsObj == null) {
                        throw new IllegalArgumentException("The 'words' param is not passed to 'num_valid_substrings' function!");
                    }
                    String wordsPath = (String) wordsObj;
                    try {
                        _words = new HashSet<>(FileUtils.readLines(new File(wordsPath)));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                @Override
                public double call(String value) {
                    int count = 0;
                    int N = value.length();
                    for (int i = 0; i < N - 1; i++)
                        for (int j = i + 2; j < N + 1; j++) {
                            if (_words.contains(value.substring(i, j)))
                                count += 1;
                        }
                    return count;
                }
            });
            put("entropy", new StringPropertiesUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {}
                @Override
                public double call(String value) {
                    HashMap<Character, Integer> freq = new HashMap<>();
                    for (int i = 0; i < value.length(); i++) {
                        char c = value.charAt(i);
                        Integer count = freq.get(c);
                        if (count == null) freq.put(c, 1);
                        else freq.put(c, count + 1);
                    }
                    double sume = 0;
                    int N = value.length();
                    double n;
                    for (char c : freq.keySet()) {
                        n = freq.get(c);
                        sume += -n / N * Math.log(n / N) / Math.log(2);
                    }
                    return sume;
                }
            });
            put("strlen", new StringPropertiesUnaryFunction() {
                @Override
                public void initialize(Map<String, Object> params) {
                }

                @Override
                public double call(String value) {
                    return value.length();
                }
            });
        }};

        public static final String TRANSFORMER_ID = "hex.genmodel.mojopipeline.transformers.StringPropertiesUnaryTransform";
        
        public static StringPropertiesUnaryFunction getFunction(String functionName) {
            final StringPropertiesUnaryFunction function = _supportedFunctions.get(functionName);
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
            final StringPropertiesUnaryFunction function = Factory.getFunction(functionName);
            function.initialize(params);
            return new StringPropertiesUnaryTransform(iindcies, oindices, function);
        }
    }
}
