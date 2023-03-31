package hex.genmodel.mojopipeline.transformers;

import ai.h2o.mojos.runtime.api.backend.ReaderBackend;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.transforms.MojoTransform;
import ai.h2o.mojos.runtime.transforms.MojoTransformBuilderFactory;
import hex.genmodel.mojopipeline.parsing.ParameterParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringGrepTransform extends MojoTransform {
    
    Pattern _pattern =  null;
    Boolean _invert = null;
    Boolean _outputLogical = null;

    StringGrepTransform(int[] iindices, int[] oindices, Pattern pattern, Boolean invert, Boolean outputLogical) {
        super(iindices, oindices);
        _pattern = pattern;
        _invert = invert;
        _outputLogical = outputLogical;
    }

    @Override
    public void transform(MojoFrame frame) {
        String[] a = (String[]) frame.getColumnData(iindices[0]);
        Double[] o = (Double[]) frame.getColumnData(oindices[0]);
        OutputWriter writer = OutputWriter.makeWriter(o, _outputLogical, 1, _invert);
        Matcher matcher = _pattern.matcher("");
        for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
            if (a[i] == null) {
                writer.addNA(i);
            } else {
                matcher.reset(a[i]);
                writer.addRow(i, matcher.find());
            }
        }
    }

    private static abstract class OutputWriter {
        static final double MATCH = 1;
        static final double NO_MATCH = 0;

        Double[] _outputArray;
        long _start;
        boolean _invert;

        OutputWriter(Double[] outputArray, long start, boolean invert) {
            _outputArray = outputArray;
            _start = start;
            _invert = invert;
        }

        abstract void addNA(int row);
        abstract void addRow(int row, boolean matched);

        static OutputWriter makeWriter(Double[] outputArray, boolean outputLogical, long start, boolean invert) {
            if (outputLogical) {
                return new IndicatorWriter(outputArray, start, invert);
            } else {
                return new PositionWriter(outputArray, start, invert);
            }
        }

    }

    private static class IndicatorWriter extends OutputWriter {
        IndicatorWriter(Double[] outputArray, long start, boolean invert) {
            super(outputArray, start, invert);
        }

        @Override
        void addNA(int row) {
            _outputArray[row] = _invert ? MATCH : NO_MATCH;
        }

        @Override
        void addRow(int row, boolean matched) {
            _outputArray[row] = matched != _invert ? MATCH : NO_MATCH;
        }
    }

    private static class PositionWriter extends OutputWriter {
        PositionWriter(Double[] outputArray, long start, boolean invert) {
            super(outputArray, start, invert);
        }

        @Override
        void addNA(int row) {
            if (_invert) {
                _outputArray[row] = _start + (double)row;
            }
        }

        @Override
        void addRow(int row, boolean matched) {
            if (matched != _invert) {
                _outputArray[row] = _start + (double)row;
            }
        }
    }

    public static class Factory implements MojoTransformBuilderFactory {

        public static final String TRANSFORMER_ID = "hex.genmodel.mojopipeline.transformers.StringGrepTransform";
       
        public static boolean functionExists(String functionName) {
            return functionName.equals("grep");
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
            Object ignoreCaseObj = params.get("ignore_case");
            if (ignoreCaseObj == null) {
                throw new IllegalArgumentException("The 'ignore_case' param is not passed to 'grep' function!");
            }
            boolean ignoreCase = ParameterParser.paramValueToBoolean(ignoreCaseObj);
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;

            Object invertObj = params.get("invert");
            if (invertObj == null) {
                throw new IllegalArgumentException("The 'invert' param is not passed to 'grep' function!");
            }
            boolean invert = ParameterParser.paramValueToBoolean(invertObj);

            Object outputLogicalObj = params.get("output_logical");
            if (outputLogicalObj == null) {
                throw new IllegalArgumentException("The 'output_logical' param is not passed to 'grep' function!");
            }
            boolean outputLogical = ParameterParser.paramValueToBoolean(outputLogicalObj);

            Object patternObj = params.get("regex");
            if (patternObj == null) {
                throw new IllegalArgumentException("The 'pattern' param is not passed to 'grep' function!");
            }
            String stringPattern = (String)patternObj;
            Pattern pattern = Pattern.compile(stringPattern, flags);
            
            return new StringGrepTransform(
                iindcies,
                oindices,
                pattern,
                invert,
                outputLogical
            );
        }
    }
}
