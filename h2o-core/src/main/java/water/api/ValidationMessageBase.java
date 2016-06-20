package water.api;

import hex.ModelBuilder;
import water.util.Log;
import water.util.PojoUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Model builder parameter validation message schema.
 */
public class ValidationMessageBase<I extends ModelBuilder.ValidationMessage, S extends ValidationMessageBase<I, S>>
    extends SchemaV3<I, S> {
  @API(help = "Type of validation message (ERROR, WARN, INFO, HIDE)", direction = API.Direction.OUTPUT)
  public String message_type;

  @API(help = "Field to which the message applies", direction = API.Direction.OUTPUT)
  public String field_name;

  @API(help = "Message text", direction = API.Direction.OUTPUT)
  public String message;

  /**
   * Map impl field names in the validation messages to schema field names,
   * called <i>after</i> behavior of stripping leading _ characters.
   */
  public static void mapValidationMessageFieldNames(ValidationMessageBase[] validation_messages, String[] from, String[] to) {
    if (null == from && null == to)
      return;
    if (null == from || null == to)
      throw new IllegalArgumentException("Bad parameter name translation arrays; one is null and the other isn't.");
    Map<String, String> translations = new HashMap<>();
    for (int i = 0; i < from.length; i++) {
      translations.put(from[i], to[i]);
    }

    for( ValidationMessageBase vm : validation_messages) {
      if (null == vm) {
        Log.err("Null ValidationMessageBase for ModelBuilderSchema.");
        continue;
      }

      if (null == vm.field_name) {
        Log.err("Null field_name: " + vm);
        continue;
      }
      if (translations.containsKey(vm.field_name))
        vm.field_name = translations.get(vm.field_name);
    }
  }

  public I createImpl() {
    return (I) new ModelBuilder.ValidationMessage(Log.valueOf(message_type), field_name, message);
  }

  // Version&Schema-specific filling from the implementation object
  public S fillFromImpl(ModelBuilder.ValidationMessage vm) {
    PojoUtils.copyProperties(this, vm, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    this.message_type = Log.LVLS[vm.log_level()]; // field name changed
    if (this.field_name != null) {
      if (this.field_name.startsWith("_"))
        this.field_name = this.field_name.substring(1);
      else
        Log.warn("Expected all ValidationMessage field_name values to have leading underscores; ignoring: " + field_name);
    }
    return (S) this;
  }
}
