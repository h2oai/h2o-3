package water.parser.orc;

import water.fvec.Vec;

/**
 * Utilities to work with Orc schema.
 */
public final class OrcUtil {

  /** Return true if the given schema can be transformed
   * into h2o type.
   *
   * @param s  orc field name in string
   * @return  true if the schema can be transformed into H2O type
   */
  public static boolean isSupportedSchema(String s) {
    switch (s.toLowerCase()) {
      case "boolean":
      case "bigint":  // long
 //     case "binary":  // TODO: add support for binary type after understanding what it is.
      case "char":
      case "date":
      case "decimal":
      case "double":
      case "float":
      case "int":
      case "smallint":
      case "string":
//      case "timestamp":  // TODO: add support for binary type after understanding what it is.
      case "tinyint":
      case "varchar":
        return true;
      default:
        return false;
    }
  }

  /**
   * Transform Orc schema into H2O type.
   * Transform Orc schema into H2O type.
   *
   * @param s  Orc data type
   * @return  a byte representing H2O column type
   * @throws IllegalArgumentException  if schema is not supported
   */
  public static byte schemaToColumnType(String s) {
    switch (s.toLowerCase()) {
      case "boolean":
      case "smallint":
      case "tinyint":
      case "bigint":
      case "int":
      case "float":
      case "double":
      case "decimal":
        return Vec.T_NUM;
//      case "timestamp":  // TODO: add support for binary type after understanding what it is.
      case "date":
          return Vec.T_TIME;
      case "string":
      case "varchar":
 //     case "binary":  // TODO: "binary" not supported, not sure how to interpret it.
      case "char":
        return Vec.T_STR;
      default:
        throw new IllegalArgumentException("Unsupported Orc schema type: " + s);
    }
  }
}
