package hex.knn;

import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Iterator;

public class KNNDistanceTask extends MRTask<KNNDistanceTask>  {

    public int _k;
    public Chunk[] _queryData;
    public KNNDistance _distance;
    public KNNHashMap<String, TopNTreeMap<KNNKey, Object>> _topNNeighboursMaps;
    public String _idColumn;
    public String _responseColumn;
    public int _idIndex;
    public int _responseIndex;
    public byte _idColumnType;

    /**
     * Calculate distances dor a particular chunk
     */
    public KNNDistanceTask(int k, Chunk[] query, KNNDistance distance, int idIndex, String idColumn, byte idType, int responseIndex, String responseColumn){
        this._k = k;
        this._queryData = query;
        this._distance = distance;
        this._topNNeighboursMaps = new KNNHashMap<>();
        this._idColumn = idColumn;
        this._responseColumn = responseColumn;
        this._idIndex = idIndex;
        this._responseIndex = responseIndex;
        this._idColumnType = idType;
    }

    @Override
    public void map(Chunk[] cs) {
        int queryColNum = _queryData.length;
        long queryRowNum = _queryData[0]._len;
        int inputColNum = cs.length;
        int inputRowNum = cs[0]._len;
        assert queryColNum == inputColNum: "Query data frame and input data frame should have the same columns number.";
        for (int i = 0; i < queryRowNum; i++) { // go over all query data rows
            TopNTreeMap<KNNKey, Object> distancesMap = new TopNTreeMap<>(_k);
            String queryDataId = _idColumnType == Vec.T_STR ? _queryData[_idIndex].stringAt(i) : String.valueOf(_queryData[_idIndex].at8(i));
            for (int j = 0; j < inputRowNum; j++) { // go over all input data rows
                String inputDataId = _idColumnType == Vec.T_STR ? cs[_idIndex].stringAt(j) : String.valueOf(cs[_idIndex].at8(j));
                long inputDataCategory =  cs[_responseIndex].at8(j);
                // if(queryDataId.equals(inputDataId)) continue; // the same id included or not?
                _distance.initializeValues();
                for (int k = 0; k < inputColNum; k++) { // go over all columns
                    if (k == _idIndex || k == _responseIndex) continue;
                    double queryColData = _queryData[k].atd(i);
                    double inputColData = cs[k].atd(j);
                    _distance.calculateValues(queryColData, inputColData);
                }
                double dist = _distance.result();

                distancesMap.put(new KNNKey(inputDataId, dist), inputDataCategory);
            }
            _topNNeighboursMaps.put(queryDataId, distancesMap);
        }
    }

    @Override
    public void reduce(KNNDistanceTask mrt) {
        KNNHashMap<String, TopNTreeMap<KNNKey, Object>> inputMap = mrt._topNNeighboursMaps;
        this._topNNeighboursMaps.reduce(inputMap);
    }

    /**
     * Get data from maps to Frame
     * @param vecs
     * @return filled array of vecs with calculated data 
     */
    public Vec[] fillVecs(Vec[] vecs){
        for (int i = 0; i < vecs[0].length(); i++) {
            String id = _idColumnType == Vec.T_STR ? vecs[0].stringAt(i) : String.valueOf(vecs[0].at8(i));
            TopNTreeMap<KNNKey, Object> topNMap = _topNNeighboursMaps.get(id);
            Iterator<KNNKey> distances = topNMap.keySet().stream().iterator();
            Iterator<Object> responses = topNMap.values().iterator();
            for (int j = 1; j < _k+1; j++) {
                KNNKey key = distances.next();
                String keyString = key.key.toString();
                vecs[j].set(i, key.value);
                if(_idColumnType == Vec.T_STR){
                    vecs[_k + j].set(i, keyString);
                } else {
                    vecs[_k + j].set(i, Integer.parseInt(keyString));
                }
                vecs[2 * _k + j].set(i, (long) responses.next());
            }
        }
        return vecs;
    }

    /**
     * Generate output frame with calculated distances.
     * @return
     */
    public Frame outputFrame() {
        int newVecsSize = _k*3+1;
        Vec[] vecs = new Vec[newVecsSize];
        String[] names = new String[newVecsSize];
        boolean isStringId = _idColumnType == Vec.T_STR;
        Vec id = Vec.makeCon(0, _queryData[0].len(), false);
        for (int i = 0; i < _queryData[_idIndex].len(); i++) {
            if(isStringId) {
                id.set(i, _queryData[_idIndex].stringAt(i));
            } else {
                id.set(i, _queryData[_idIndex].atd(i));
            }
        }
        vecs[0] = id;
        names[0] = _idColumn;
        for (int i = 1; i < _k+1; i++) {
            // names of columns
            names[i] = "dist_"+i; // this could be customized
            names[_k+i] = _idColumn+"_"+i; // this could be customized
            names[2*_k+i] = _responseColumn+"_"+i; // this could be customized
            vecs[i] = id.makeZero();
            vecs[i] = vecs[i].toNumericVec();
            vecs[_k+i] = id.makeZero();
            if (isStringId) vecs[_k+i].toStringVec();
            vecs[2*_k+i] = id.makeZero();
            vecs[2*_k+i] = vecs[2*_k+i].toNumericVec();
        }
        vecs = fillVecs(vecs);
        return new Frame(Key.make("KNN_distances_tmp"), names, vecs);
    }
}
