package water.automl.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.RequestSchemaV3;

public class AutoMLExportV99 extends RequestSchemaV3<Iced, AutoMLExportV99> {
        // Input fields
        @API(help="Save imported AutoML under given key into DKV.", json=false)
        public KeyV3 automl_id;

        @API(help="Source directory (hdfs, s3, local) containing serialized AutoML")
        public String dir;

        @API(help="Override existing model in case it exists or throw exception if set to false")
        public boolean force = true;

}
