package water.parser.orc;

import org.apache.avro.Schema;
import water.fvec.Vec;

import java.util.List;

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
      case "smallint":
      case "tinyint":
      case "bigint":
      case "int":
      case "long":
      case "float":
      case "double":
      case "timestamp":
      case "decimal":
      case "string":
      case "varchar":
      case "char":
      case "date":
      case "binary":
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
      case "long":
      case "float":
      case "double":
      case "timestamp":
      case "decimal":
        return Vec.T_NUM;
      case "enum":
        return Vec.T_CAT;
      case "string":
        return Vec.T_STR;
      case "null":
        return Vec.T_BAD;
      case "varchar":
      case "char":
      case "date":
      case "binary":
        return Vec.T_STR;
      default:
        throw new IllegalArgumentException("Unsupported Orc schema type: " + s);
    }
  }

  static String[] getDomain(Schema fieldSchema) {
    if (fieldSchema.getType() == Schema.Type.ENUM) {
      return fieldSchema.getEnumSymbols().toArray(new String[] {});
    } else if (fieldSchema.getType() == Schema.Type.UNION) {
      List<Schema> unionSchemas = fieldSchema.getTypes();
      if (unionSchemas.size() == 1) {
        return getDomain(unionSchemas.get(0));
      } else if (unionSchemas.size() == 2) {
        Schema s1 = unionSchemas.get(0);
        Schema s2 = unionSchemas.get(1);
        if (s1.getType() == Schema.Type.NULL) return getDomain(s2);
        else if (s2.getType() == Schema.Type.NULL) return getDomain(s1);
      }
    }
    throw new IllegalArgumentException("Cannot get domain from field: " + fieldSchema);
  }

  /**
   * Transform Avro schema into its primitive representation.
   *
   * @param s  avro schema
   * @return  primitive type as a result of transformation
   * @throws IllegalArgumentException if the schema has no primitive transformation
   */
  public static Schema.Type toPrimitiveType(Schema s) {
    Schema.Type typ = s.getType();
    switch(typ) {
      case BOOLEAN:
      case INT:
      case LONG:
      case FLOAT:
      case DOUBLE:
      case ENUM:
      case STRING:
      case NULL:
      case BYTES:
        return typ;
      case UNION:
        List<Schema> unionSchemas = s.getTypes();
        if (unionSchemas.size() == 1) {
          return toPrimitiveType(unionSchemas.get(0));
        } else if (unionSchemas.size() == 2) {
          Schema s1 = unionSchemas.get(0);
          Schema s2 = unionSchemas.get(1);
          if (s1.getType().equals(Schema.Type.NULL)) return toPrimitiveType(s2);
          else if (s2.getType().equals(Schema.Type.NULL)) return toPrimitiveType(s1);
        }
      default:
        throw new IllegalArgumentException("Unsupported Orc schema type: " + s);
    }
  }

  /**
   * The method "flattenize" the given Avro schema.
   * @param s  Avro schema
   * @return  List of supported fields which were extracted from original Schema
   */
//  public static Schema.Field[] flatSchema(Schema s) {
//    List<Schema.Field> fields = s.getFields();
//    Schema.Field[] flatSchema = new Schema.Field[fields.size()];
//    int cnt = 0;
//    for (Schema.Field f : fields) {
//      if (isSupportedSchema(f.schema())) {
//        flatSchema[cnt] = f;
//        cnt++;
//      }
//    }
//    // Return resized array
//    return cnt != flatSchema.length ? Arrays.copyOf(flatSchema, cnt) : flatSchema;
//  }
}
