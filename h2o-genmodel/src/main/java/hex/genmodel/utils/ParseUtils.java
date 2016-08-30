package hex.genmodel.utils;

/**
 * Helper function for parsing the serialized model.
 */
public class ParseUtils {

    public static double[] parseArrayOfDoubles(String input) {
        if (!(input.startsWith("[") && input.endsWith("]")))
            throw new NumberFormatException("Array should be enclosed in square brackets");
        String[] parts = input.substring(1, input.length()-1).split(",");
        double[] res = new double[parts.length];
        for (int i = 0; i < parts.length; i++)
            res[i] = Double.parseDouble(parts[i]);
        return res;
    }

    public static Object tryParse(String input) {
        if (input.equals("true")) return true;
        if (input.equals("false")) return false;

        try { return Integer.parseInt(input); }
        catch (NumberFormatException ignored) {}

        try { return Double.parseDouble(input); }
        catch (NumberFormatException ignored) {}

        try { return parseArrayOfDoubles(input); }
        catch (NumberFormatException ignored) {}

        return input;
    }
}
