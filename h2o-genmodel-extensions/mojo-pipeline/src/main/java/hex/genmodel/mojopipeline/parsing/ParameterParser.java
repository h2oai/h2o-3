package hex.genmodel.mojopipeline.parsing;

public class ParameterParser {
    public static boolean paramValueToBoolean(Object paramValue) {
        if (paramValue instanceof String) {
            return Boolean.parseBoolean((String)paramValue);
        } else if (paramValue instanceof Double) {
            return (Double)paramValue > 0.0;
        } else {
            throw new UnsupportedOperationException(
                    String.format(
                            "Unable convert a parameter value %s of type %s to Boolean.",
                            paramValue,
                            paramValue.getClass().getName()));
        }
    }
}
