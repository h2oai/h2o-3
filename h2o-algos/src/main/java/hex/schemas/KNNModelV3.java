package hex.schemas;

import hex.knn.KNNModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class KNNModelV3 extends ModelSchemaV3<KNNModel, KNNModelV3, KNNModel.KNNParameters, KNNV3.KNNParametersV3, KNNModel.KNNOutput, KNNModelV3.KNNModelOutputV3> {

    public static final class KNNModelOutputV3 extends ModelOutputSchemaV3<KNNModel.KNNOutput, KNNModelOutputV3> {
        @API(help="Key of frame with calculated distances.")
        public String distances;
        
        @Override public KNNModelOutputV3 fillFromImpl(KNNModel.KNNOutput impl) {
            KNNModelOutputV3 knnv3 = super.fillFromImpl(impl);
            knnv3.distances = impl.getDistancesKey().toString();
            return knnv3;
        }
    }

    public KNNV3.KNNParametersV3 createParametersSchema() { return new KNNV3.KNNParametersV3(); }
    public KNNModelOutputV3 createOutputSchema() { return new KNNModelOutputV3(); }

    @Override public KNNModel createImpl() {
        KNNModel.KNNParameters parms = parameters.createImpl();
        return new KNNModel( model_id.key(), parms, null );
    }
}
