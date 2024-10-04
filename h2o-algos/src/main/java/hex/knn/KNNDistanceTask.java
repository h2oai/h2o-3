package hex.knn;

import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Iterator;

public class KNNDistanceTask extends MRTask<KNNDistanceTask>  {
    
    public int _k;
    public Frame _queryData;
    public KNNDistance _distance;
    public KNNHashMap<String, TopNTreeMap<KNNKey, Object>> _topNNeighboursMaps;
    public String _idColumn;
    public String _responseColumn;
    public int _idIndex;
    public int _responseIndex;
    public byte _idColumnType;

    /**
     * 
     * @param query Frame where the first column = id, next columns are data
     * @param distance Type of distance calculation
     */
    public KNNDistanceTask(int k, Frame query, KNNDistance distance, String idColumn, String responseColumn){
        this._k = k;
        this._queryData = query;
        this._distance = distance;
        this._topNNeighboursMaps = new KNNHashMap<>();
        this._idColumn = idColumn;
        this._responseColumn = responseColumn;
        this._idIndex = query.find(idColumn);
        this._responseIndex = query.find(responseColumn);
        this._idColumnType = query.vec(_idIndex).get_type();
    }

    @Override
    public void map(Chunk[] cs) {
        long queryRowNum = _queryData.numRows();
        int queryColNum = _queryData.numCols();
        int inputColNum = cs.length;
        int inputRowNum = cs[0]._len;
        assert queryColNum == inputColNum: "Query data frame and input data frame should have the same columns number.";
        for (int i = 0; i < queryRowNum; i++) { // go over all query data rows
            TopNTreeMap<KNNKey, Object> distancesMap = new TopNTreeMap<>(_k);
            String queryDataId = _idColumnType == Vec.T_STR ? _queryData.vec(_idIndex).stringAt(i) : String.valueOf(_queryData.vec(_idIndex).at8(i));
            for (int j = 0; j < inputRowNum; j++) { // go over all input data rows
                String inputDataId = _idColumnType == Vec.T_STR ? cs[_idIndex].stringAt(j) : String.valueOf(cs[_idIndex].at8(j));
                long inputDataCategory =  cs[_responseIndex].at8(j);
                if(queryDataId.equals(inputDataId)) continue;
                double[] distValues = _distance.initializeValues();
                for (int k = 0; k < inputColNum; k++) { // go over all columns
                    if (k == _idIndex || k == _responseIndex) continue;
                    double queryColData = _queryData.vec(k).at(i);
                    double inputColData = cs[k].atd(j);
                    distValues = _distance.calculateValues(queryColData, inputColData, distValues);
                }
                double dist = _distance.result(distValues);
                
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
    
    public Vec[] fillVecs(Vec[] vecs){
        for (int i = 0; i < vecs[0].length(); i++) {
            // id is on 0 index in vecs
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
                    vecs[_k + j].set(i, Integer.valueOf(keyString));
                }
                vecs[2 * _k + j].set(i, (long)responses.next());
            }
        }
        return vecs;
    }

    public Frame outputFrame() {
        int newVecsSize = _k*3+1;
        Vec[] vecs = new Vec[newVecsSize];
        String[] names = new String[newVecsSize];
        Vec id = _queryData.vec(_idIndex); 
        vecs[0] = id;
        String idColName = _queryData.name(_idIndex);
        String responseColName = _queryData.name(_responseIndex);
        names[0] = idColName;
        for (int i = 1; i < _k+1; i++) {
            names[i] = "dist_"+i;
            names[_k+i] = idColName+"_"+i;
            names[2*_k+i] = responseColName+"_"+i;
            vecs[i] = id.makeZero();
            vecs[i] = vecs[i].toNumericVec();
            vecs[_k+i] = id.makeZero();
            if (_idColumnType == Vec.T_STR) vecs[_k+i].toStringVec();
            vecs[2*_k+i] = id.makeZero();
            vecs[2*_k+i] = vecs[2*_k+i].toNumericVec();
        }
        vecs = fillVecs(vecs);
        Frame out = new Frame(Key.make("KNN_distances"), names, vecs);
        DKV.put(out);
        return out;
    }
}
