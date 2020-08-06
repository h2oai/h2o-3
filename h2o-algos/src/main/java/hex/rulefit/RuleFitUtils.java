package hex.rulefit;

public class RuleFitUtils {

    public static String[] getPathNames(int modelId, int numCols, String[] names) {
        String[] pathNames = new String[numCols];
        for (int i = 0; i < numCols; i++) {
            pathNames[i] = "tree_" + modelId + "." + names[i];
        }
        return pathNames;
    }
}
