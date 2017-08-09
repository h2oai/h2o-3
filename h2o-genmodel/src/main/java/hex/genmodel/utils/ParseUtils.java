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
            res[i] = Double.parseDouble(parts[i].trim());
        return res;
    }

    public static int[] parseArrayOfInts(String input) {
        if (!(input.startsWith("[") && input.endsWith("]")))
            throw new NumberFormatException("Array should be enclosed in square brackets");
        String[] parts = input.substring(1, input.length()-1).split(",");
        int[] res = new int[parts.length];
        for (int i = 0; i < parts.length; i++)
            res[i] = Integer.parseInt(parts[i].trim());
        return res;
    }

    public static Object tryParse(String input, Object defVal) {
        if (input.equals("null")) return defVal;

        if (defVal instanceof Boolean) {
            return Boolean.valueOf(input);
        } else {
            if (input.equals("true")) return true;
            if (input.equals("false")) return false;
        }

        if ("[]".equals(input) && (defVal != null) && defVal.getClass().isArray())
            return defVal;

        try { return Integer.parseInt(input); }
        catch (NumberFormatException e) {
            if ((defVal instanceof Number) && ! (defVal instanceof Double || defVal instanceof Float))
                throw e; // integer number expected but couldn't be parsed
        }

        try { return Double.parseDouble(input); }
        catch (NumberFormatException e) {
            if (defVal instanceof Number)
                throw e; // number expected but couldn't be parsed
        }

        try { return parseArrayOfInts(input); }
        catch (NumberFormatException e) {
            if (defVal instanceof int[]) throw e; // int array expected
        }

        try { return parseArrayOfDoubles(input); }
        catch (NumberFormatException e) {
            if (defVal instanceof double[]) throw e; // double array expected
        }

        return input;
    }
}
