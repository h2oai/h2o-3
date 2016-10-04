package water.api;

import com.google.gson.*;
import water.api.schemas3.FrameV3;
import water.api.schemas3.KeyV3;

import java.lang.reflect.Type;

/**
 * Custom Gson serialization for our REST API, which does things like turn a String of a Key into
 * a Key object automagically.
 */
public class H2oRestGsonHelper {
  /**
   * Create a Gson JSON serializer / deserializer that has custom handling for certain H2O classes for
   * which our REST API does automagic type conversions.
   * <p>
   * TODO: this method is copy-pasted from H2oApi.java in a more limited form; refactor.
   * See the comments there.
   */

  public static Gson createH2oCompatibleGson() {
    return new GsonBuilder()
            // .registerTypeAdapterFactory(new ModelV3TypeAdapter())
            .registerTypeAdapter(KeyV3.class, new KeySerializer())
            .registerTypeAdapter(FrameV3.ColSpecifierV3.class, new ColSerializer())
            // .registerTypeAdapter(ModelBuilderSchema.class, new ModelDeserializer())
            // .registerTypeAdapter(ModelSchemaBaseV3.class, new ModelSchemaDeserializer())
            .create();
  }

  /**
   * Keys get sent as Strings and returned as objects also containing the type and URL,
   * so they need a custom GSON serializer.
   */
  private static class KeySerializer implements JsonSerializer<KeyV3>, JsonDeserializer<KeyV3> {
    @Override
    public JsonElement serialize(KeyV3 key, Type typeOfKey, JsonSerializationContext context) {
      return new JsonPrimitive(key.name);
    }
    @Override
    public KeyV3 deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
      if (json.isJsonNull()) return null;
      JsonObject jobj = json.getAsJsonObject();
      String type = jobj.get("type").getAsString();
      switch (type) {
        // TODO: dynamically generate all possible cases
        case "Key<Model>": return context.deserialize(jobj, KeyV3.ModelKeyV3.class);
        case "Key<Job>":   return context.deserialize(jobj, KeyV3.JobKeyV3.class);
        case "Key<Grid>":  return context.deserialize(jobj, KeyV3.GridKeyV3.class);
        case "Key<Frame>": return context.deserialize(jobj, KeyV3.FrameKeyV3.class);
        default: throw new JsonParseException("Unable to deserialize key of type " + type);
      }
    }
  }

  private static class ColSerializer implements JsonSerializer<FrameV3.ColSpecifierV3>, JsonDeserializer<FrameV3.ColSpecifierV3> {
    @Override
    public JsonElement serialize(FrameV3.ColSpecifierV3 col, Type typeOfCol, JsonSerializationContext context) {
      return new JsonPrimitive(col.column_name); // UGH: external-facing, generated POJO uses camelCase. . .
    }

    @Override
    public FrameV3.ColSpecifierV3 deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
      if (json.isJsonNull()) return null;

      return new FrameV3.ColSpecifierV3(json.getAsString());
    }
  }
}
