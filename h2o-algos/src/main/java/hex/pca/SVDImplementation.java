package hex.pca;

/**
 * @author mathemage <ha@h2o.ai>
 *         created on 2.5.17
 */
public enum SVDImplementation {
  EVD_MTJ_DENSEMATRIX, EVD_MTJ_SYMM, MTJ, JAMA;
  final static SVDImplementation fastestImplementation = EVD_MTJ_SYMM;    // set to the fastest implementation
  
  public static SVDImplementation getFastestImplementation() {
    return fastestImplementation;
  }
  
  public static String[] getEnumNames() {
    SVDImplementation[] svdImplementations = values();
    String[] enumNames = new String[svdImplementations.length];
    int nameIndex = 0;
    for (SVDImplementation enumType: svdImplementations) {
      enumNames[nameIndex++] = enumType.toString();
    }
    return enumNames;
  }
}
