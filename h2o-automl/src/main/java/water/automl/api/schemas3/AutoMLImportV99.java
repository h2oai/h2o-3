package water.automl.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelImportV3;
import water.api.schemas3.RequestSchemaV3;

public class AutoMLImportV99  extends RequestSchemaV3<Iced, AutoMLImportV99> {
        @API(help="Source directory (hdfs, s3, local) containing serialized AutoML")
        public String dir;
}
