package hex.genmodel.mojopipeline.transformers;

import ai.h2o.mojos.runtime.api.backend.ReaderBackend;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.transforms.MojoTransform;
import ai.h2o.mojos.runtime.transforms.MojoTransformBuilderFactory;
import org.apache.commons.lang.mutable.Mutable;
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;
import org.joda.time.DateTimeZone;
import org.joda.time.MutableDateTime;

import java.util.HashMap;
import java.util.Map;

public class TimeUnaryTransform extends MojoTransform {

    TimeUnaryFunction _function;
    DateTimeZone _timeZone;

    TimeUnaryTransform(int[] iindices, int[] oindices, TimeUnaryFunction function, DateTimeZone timeZone) {
        super(iindices, oindices);
        _function = function;
        _timeZone = timeZone;
    }

    @Override
    public void transform(MojoFrame frame) {
        double[] a = (double[]) frame.getColumnData(iindices[0]);
        String[] factors = _function.factors();
        MutableDateTime dataTime = new MutableDateTime(0, _timeZone);
        if (factors == null) {
            double[] o = (double[]) frame.getColumnData(oindices[0]);
            for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
                if (Double.isNaN(a[i])) {
                    o[i] = Double.NaN;
                } else {
                    dataTime.setMillis((long) a[i]);
                    o[i] = _function.call(dataTime);
                }
            }
        } else {
            String[] o = (String[]) frame.getColumnData(oindices[0]);
            for (int i = 0, nrows = frame.getNrows(); i < nrows; i++) {
                if (Double.isNaN(a[i])) {
                    o[i] = null;
                } else {
                    dataTime.setMillis((long) a[i]);
                    o[i] = factors[(int)_function.call(dataTime)];
                }
            }
        }
    }

   interface TimeUnaryFunction {
        double call(MutableDateTime value);
        String[] factors();
    }

    public static class Factory implements MojoTransformBuilderFactory {
        
        private static final HashMap<String,TimeUnaryFunction> _supportedFunctions = new HashMap<String,TimeUnaryFunction>() {{
            put("day", new TimeUnaryFunction() {
                @Override
                public double call(MutableDateTime value) { return value.getDayOfMonth(); }

                @Override
                public String[] factors() { return null; }
            });
            put("dayOfWeek", new TimeUnaryFunction() {
                @Override
                public double call(MutableDateTime value) { return value.getDayOfWeek() - 1; }

                @Override
                public String[] factors() { return new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}; }
            });
            put("hour", new TimeUnaryFunction() {
                @Override
                public double call(MutableDateTime value) { return value.getHourOfDay(); }

                @Override
                public String[] factors() { return null; }
            });
            put("millis", new TimeUnaryFunction() {
                @Override
                public double call(MutableDateTime value) { return value.getMillisOfSecond(); }

                @Override
                public String[] factors() { return null; }
            });
            put("minute", new TimeUnaryFunction() {
                @Override
                public double call(MutableDateTime value) { return value.getMinuteOfHour(); }

                @Override
                public String[] factors() { return null; }
            });
            put("month", new TimeUnaryFunction() {
                @Override
                public double call(MutableDateTime value) { return value.getMonthOfYear(); }

                @Override
                public String[] factors() { return null; }
            });
            put("second", new TimeUnaryFunction() {
                @Override
                public double call(MutableDateTime value) { return value.getSecondOfMinute(); }
                
                @Override
                public String[] factors() { return null; }
            });
            put("week", new TimeUnaryFunction() {
                @Override
                public double call(MutableDateTime value) { return value.getWeekOfWeekyear(); }

                @Override
                public String[] factors() { return null; }
            });
            put("year", new TimeUnaryFunction() {
                @Override
                public double call(MutableDateTime value) { return value.getYear(); }

                @Override
                public String[] factors() { return null; }
            });
        }};

        public static final String TRANSFORMER_ID = "hex.genmodel.mojopipeline.transformers.TimeUnaryTransform";
        
        public static TimeUnaryFunction getFunction(String functionName) {
            final TimeUnaryFunction function = _supportedFunctions.get(functionName);
            if (function == null) {
                throw new UnsupportedOperationException(
                        String.format("The function '%s' is not supported unary time transformation.", functionName));
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
            final TimeUnaryFunction function = Factory.getFunction(functionName);
            final String timeZoneId = (String) params.get("timezone");
            final DateTimeZone timeZone = DateTimeZone.forID(timeZoneId);
            return new TimeUnaryTransform(iindcies, oindices, function, timeZone);
        }
    }
}
