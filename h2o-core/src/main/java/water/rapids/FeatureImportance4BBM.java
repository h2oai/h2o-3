package water.rapids;

import hex.AUC2;
import hex.Model;
//import hex.VarImp;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.*;


public class FeatureImportance4BBM /* extends VarImp */ {
    
    private Model _model;
    public Frame _ouput;
    private Frame _train_fr; // original dataset used to train model
    private Frame _validate_dataset; // original dataset used to train model
    private Frame _predcited_frame;

    private boolean _data_in_holdOut_form; // do I have the data split in train and validation, if not spliting will happen

    private String _responce_col;

    private AUC2 og_auc;

    private double original_mse;
    private double sq_err;
            
    public FeatureImportance4BBM(Model model) {_model = model;}

    public FeatureImportance4BBM(Model model, Frame data_set, Frame prediction_frame) {
        _model = model;
        _train_fr = data_set;
        _data_in_holdOut_form = false;
        _validate_dataset = null;
        _predcited_frame = prediction_frame;
        _responce_col = _train_fr.name(0); // first column as default
    }

    public FeatureImportance4BBM(Model model, Frame data_set, Frame prediction_frame, String response_col) {
        _model = model;
        _train_fr = data_set;
        _data_in_holdOut_form = false;
        _validate_dataset = null;
        _predcited_frame = prediction_frame;
        _responce_col = response_col;
    }
    
    
    //MSE set as default loss function
    public void getFeatureImportance(){
        getFeatureImportance("MSE");
    }
    
    public void getFeatureImportance(String metric) {
        if (!_data_in_holdOut_form) {
            //TODO split the data if needed
        }

        String[] features = _train_fr.names();
        HashMap<String, Double> FI = new HashMap<String, Double>(_train_fr.vecs().length);

        // original error calc using MSE or AUC as a loss function
        if (metric.equals("MSE")){
            sq_err = new MathUtils.SquareError().doAll(_train_fr.vecs()[1], _predcited_frame.vecs()[0])._sum;
            original_mse = sq_err / _predcited_frame.numRows();
        }   else    {
            og_auc = new AUC2(_train_fr.vec(_responce_col), _predcited_frame.vecs()[0]); //claisifiication metric
        }
        for (int f = 1; f < _train_fr.numCols(); f++) //f = 0 is predicted col
        {
            //permuate vector
            Vec shuffled_feature = ShuffleVec.ShuffleTask.shuffle(_train_fr.vec(features[f]));
            Vec og_feature = _train_fr.replace(f, shuffled_feature);
            DKV.put(_train_fr); // "Caller must perform global update (DKV.put) on this updated frame"

            // score the model again and compute diff
            Frame new_score = _model.score(_train_fr);

            //calucalate error from new score prediction
            if (metric.equals("MSE")){ // regression
                sq_err = new MathUtils.SquareError().doAll(_train_fr.vec(_responce_col), new_score.vecs()[0])._sum;
                FI.put(features[f], original_mse / (sq_err/new_score.numRows())); // Hashmap of HI feature->FI_mse
            }   else    {  //clasification
                AUC2 auc = new AUC2(_train_fr.vec(_responce_col), new_score.vecs()[0]); //claisifiication metric
                FI.put(features[f], og_auc._auc - auc._auc);
            }
            
            //return the original data
            _train_fr.replace(f, og_feature);
            DKV.put(_train_fr); // "Caller must perform global update (DKV.put) on this updated frame"

            new_score.remove(); // clearing leaks
        }
        /*return */HashMap<String, Double> sorted_FI_mse = sortHashMapByValues(FI);
    }

    // Sort HashMap in descenting order
    private LinkedHashMap<String, Double> sortHashMapByValues(
            HashMap<String, Double> passedMap) {
        List<String> mapKeys = new ArrayList<>(passedMap.keySet());
        List<Double> mapValues = new ArrayList<>(passedMap.values());
        Collections.sort(mapKeys, Collections.reverseOrder());
        Collections.sort(mapValues, Collections.reverseOrder());

        LinkedHashMap<String, Double> sortedMap = new LinkedHashMap<>();

        Iterator<Double> valueIt = mapValues.iterator();
        while (valueIt.hasNext()) {
            Double val = valueIt.next();
            Iterator<String> keyIt = mapKeys.iterator();

            while (keyIt.hasNext()) {
                String key = keyIt.next();
                Double comp1 = passedMap.get(key);
                if (comp1.equals(val)) {
                    keyIt.remove();
                    sortedMap.put(key, val);
                    break;
                }
            }
        }
        return sortedMap;
    }
}
