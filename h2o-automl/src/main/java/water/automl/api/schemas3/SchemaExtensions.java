package water.automl.api.schemas3;

import ai.h2o.automl.Models;
import water.Iced;
import water.Key;
import water.api.schemas3.KeyV3;

public final class SchemaExtensions {

    public static class ModelsKeyV3 extends KeyV3<Iced, SchemaExtensions.ModelsKeyV3, Models> {
        public ModelsKeyV3() {}
        public ModelsKeyV3(Key<Models> key) { super(key); }
    }

}
