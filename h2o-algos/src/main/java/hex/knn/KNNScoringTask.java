package hex.knn;


import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.ArrayUtils;

public class KNNScoringTask extends MRTask<KNNScoringTask> {

    public int _k;
    public double[] _queryData;
    public KNNDistance _distance;
    public TopNTreeMap<KNNKey, Integer> _distancesMap;
    public int _idIndex;
    public int _responseIndex;
    public byte _idColumnType;
    public int _domainSize;

    /**
     * Go through the whole input frame to find the k near distances and score based on them.
     */
    public KNNScoringTask(double[] query, int k, int domainSize, KNNDistance distance, int idIndex, byte idType, int responseIndex){
        this._k = k;
        this._queryData = query;
        this._distance = distance;
        this._responseIndex = responseIndex;
        this._idIndex = idIndex;
        this._idColumnType = idType;
        this._distancesMap = new TopNTreeMap<>(_k);
        this._domainSize = domainSize;
    }

    @Override
    public void map(Chunk[] cs) {
        int inputColNum = cs.length;
        int inputRowNum = cs[0]._len;
        for (int i = 0; i < inputRowNum; i++) { // go over all input data rows
            String inputDataId = _idColumnType == Vec.T_STR ? cs[_idIndex].stringAt(i) : String.valueOf(cs[_idIndex].at8(i));
            int inputDataCategory = (int) cs[_responseIndex].at8(i);
            _distance.initializeValues();
            int j = 0;
            for (int k = 0; k < inputColNum; k++) { // go over all columns
                if(k == _idIndex || k == _responseIndex) continue; 
                double queryColData = _queryData[j++];
                double inputColData = cs[k].atd(i);
                _distance.calculateValues(queryColData, inputColData);
            }
            double dist = _distance.result();
            _distancesMap.put(new KNNKey(inputDataId, dist), inputDataCategory);
        }
    }

    @Override
    public void reduce(KNNScoringTask mrt) {
        this._distancesMap.putAll(mrt._distancesMap);
    }
    
    public double[] score(){
        double[] scores = new double[_domainSize+1];
        assert _distancesMap.size() <= _k: "Distances map size should be <= _k";
        for (int value: _distancesMap.values()){
            scores[value+1]++;
        }
        // normalize the result score by _k
        for (int i = 1; i < _domainSize+1; i++) {
            if(scores[i] != 0) {
                scores[i] = scores[i]/_k;
            }
        }
        // decide the class by the max score
        scores[0] = ArrayUtils.maxIndex(scores)-1;
        return scores;
    }
}
