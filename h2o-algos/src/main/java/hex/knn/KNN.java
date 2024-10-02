package hex.knn;

import hex.ModelBuilder;
import hex.ModelCategory;

public class KNN extends ModelBuilder<KNNModel,KNNModel.KNNParameters,KNNModel.KNNOutput> {

    protected KNN(KNNModel.KNNParameters params){
        super(params);
    }

    @Override
    protected KNNDriver trainModelImpl() {
        return new KNNDriver();
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{ModelCategory.Binomial, ModelCategory.Multinomial};
    }

    @Override
    public boolean isSupervised() {
        return true;
    }

    @Override public void init(boolean expensive) {
        super.init(expensive);
    }

    class KNNDriver extends Driver{

        @Override
        public void computeImpl() {
            
        }
    }
}


