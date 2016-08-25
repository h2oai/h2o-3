package hex.genmodel.utils;

/**
 * Helper function for parsing the serialized model.
 */
public class ParseUtils {

    public static double[] parseArrayOfDoubles(String input) {
        String[] parts = input.substring(1, input.length()-1).split(",");
        double[] res = new double[parts.length];
        for (int i = 0; i < parts.length; i++)
            res[i] = Double.parseDouble(parts[i]);
        return res;
    }

    public static Object maybeParseDouble(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            return input;
        }
    }
}
