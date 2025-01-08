package hex.knn;

import hex.*;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Frame;

public class KNN extends ModelBuilder<KNNModel,KNNModel.KNNParameters,KNNModel.KNNOutput> {
    
    public KNN(KNNModel.KNNParameters parms) {
        super(parms); 
        init(false); 
    }

    public KNN(boolean startup_once) {
        super(new KNNModel.KNNParameters(), startup_once);
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
        if( null == _parms._id_column) {
            error("_id_column", "ID column parameter not set.");
        }
        if( null == _parms._distance) {
            error("_distance", "Distance parameter not set.");
        }
    }

    class KNNDriver extends Driver {
        
        @Override
        public void computeImpl() {
            KNNModel model = null;
            Frame result = new Frame(Key.make("KNN_distances"));
            Frame tmpResult = null;
            try {
                init(true);   // Initialize parameters
                if (error_count() > 0) {
                    throw new IllegalArgumentException("Found validation errors: " + validationErrors());
                }
                model = new KNNModel(dest(), _parms, new KNNModel.KNNOutput(KNN.this));
                model.delete_and_lock(_job);
                Frame train = _parms.train();
                String idColumn = _parms._id_column;
                int idColumnIndex = train.find(idColumn);
                byte idType = train.vec(idColumnIndex).get_type();
                String responseColumn = _parms._response_column;
                int responseColumnIndex = train.find(responseColumn);
                int nChunks = train.anyVec().nChunks();
                int nCols = train.numCols();
                // split data into chunks to calculate distances in parallel task
                for (int i = 0; i < nChunks; i++) {
                    Chunk[] query = new Chunk[nCols];
                    for (int j = 0; j < nCols; j++) {
                        query[j] = train.vec(j).chunkForChunkIdx(i).deepCopy();
                    }
                    KNNDistanceTask task = new KNNDistanceTask(_parms._k, query, KNNDistanceFactory.createDistance(_parms._distance), idColumnIndex, idColumn, idType, responseColumnIndex, responseColumn);
                    tmpResult = task.doAll(train).outputFrame();
                    // merge result from a chunk
                    result = result.add(tmpResult);
                }
                DKV.put(result._key, result);
                model._output.setDistancesKey(result._key);
                Scope.untrack(result);
                
                model.update(_job);
                
                model.score(_parms.train()).delete();
                model._output._training_metrics = ModelMetrics.getFromDKV(model, _parms.train());
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (model != null) {
                    model.unlock(_job);
                }
                if (tmpResult != null) {
                    tmpResult.remove();
                }
            }
        }
    }
}


