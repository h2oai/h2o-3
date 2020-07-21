package water.rapids;

import hex.*;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;
import java.util.*;

/**
 * Calculate permutation feature importance, by shuffling randomly each feature of the passed model
 * then scoring the model with the newly created frame using One At a Time approach
 * and Morris method; creating TwoDimTable with relative, scaled, and percentage value
 *                             TwoDimTable with mean of the absolute value, and standard deviation of all features importance
 * */

public class PermutationFeatureImportance {

    private double[] _p_var_imp; // permutation feature (variable) importance, relative value      
    
    private String _response_col; 
    
    private String[] _features; 
    String[] _features_wo_res; // features without response col
    
    Vec [] response_vec;
    private Model _model;
    private Frame _train_fr; // original dataset used to train model

    /**
     * When passing only the Model without Frame (will use models frame)
     * */
    public PermutationFeatureImportance(Model model) {
        _model = model;
        _train_fr = _model._parms._train.get();
        init();
    }

    public PermutationFeatureImportance(Model model, Frame data_set, Frame prediction_frame) {
        _model = model;
        _train_fr = data_set;
        init();
    }
    
    void init(){
        _response_col = _model._parms._response_column;
        _features = _train_fr.names();
    }
    public String [] get_features_wo_res_test(){return _features_wo_res;}
    
    /**
     * Creates a new array of Strings without the response column
     * */
    public void removeResCol(){
        // remove response col from array of string to be added to TwoDimTable
        _features_wo_res = _train_fr.names();
        if (_features_wo_res[0].equals(_response_col)) {
            _features_wo_res = Arrays.copyOfRange(_features_wo_res, 1, _features_wo_res.length);
        } else { // scenario where the response_col isn't the first column 
            List<String> list = new ArrayList<String>(Arrays.asList(_features_wo_res));
            if (list.contains(_response_col))   // paranoid if check
                list.remove(_response_col);
            _features_wo_res = list.toArray(new String[0]);
        }
        _p_var_imp = new double[_features_wo_res.length]; 
        
//        _train_fr.remove(_model._output.responseName());
//        _train_fr._names
    }

    /**
     * Stores original values and j-th feature's score metric (loss function) values  
     * */
    private static class LocalMetric{
        double og_mse;
        double og_logloss;
        double og_auc;
        
        double m_f_mse; 
        double m_f_auc;
        double m_f_logloss;
    }
    
    /**
     *  Set the original values of trained metrics to LocalMetric class
     * */
    private void setOriginalMetrics(LocalMetric m) {
        ModelMetrics og_mm = ModelMetrics.getFromDKV(_model, _model._parms.train());
//        ModelMetricsBinomial og_mm = (ModelMetricsBinomial)_model._output._training_metrics;

        try{
            if (_model._output.isBinomialClassifier()){ // binomial
                if (og_mm.auc_obj() != null && !Double.isNaN(og_mm.auc_obj()._auc)){// FIXME: checck properly if modell has auc 
                    m.og_auc = og_mm.auc_obj()._auc;
                } else throw new MetricNotFoundExeption("Initial metric for binomial auc not found for model " + _model._key);
            }
            else if (_model._output.isMultinomialClassifier()) { //   multinomial
                if (!Double.isNaN(((ModelMetricsMultinomial)og_mm)._logloss)) m.og_logloss = og_mm.mse();
            }
//        else if (!Double.isNaN(og_mm.mse()))             m.og_mse = og_mm.mse();
            else if (_model._output.isSupervised()){
                m.og_mse = og_mm.mse();
            } else throw new MetricNotFoundExeption("Unhandeled model metric category for model " + _model._key);
        } catch (MetricNotFoundExeption e){
            System.err.println("ModelMetricCalculation threw an exception unchecked Classifier " + _model._key);
            e.printStackTrace();
        }
    }
     
    /**
     * Calculate loss function of scored model with shuffled feature and store it
     * */
    private LocalMetric addToFeatureToTable(LocalMetric lm, int id, Frame fr){
        try{
            ModelMetrics sh_mm = ModelMetrics.getFromDKV(_model, fr);
//        ModelMetrics mm = hex.ModelMetrics.getFromDKV(_model, _model._parms.train());
            
            switch (_model._output.getModelCategory()){
                case Regression:
                    lm.m_f_mse = sh_mm.mse() / lm.og_mse;
                    _p_var_imp[id] = lm.m_f_mse;
                    break;
                case Binomial:
                    if (sh_mm.auc_obj() != null && !Double.isNaN(sh_mm.auc_obj()._auc)) { // FIXME: check properly if model has auc 
                        lm.m_f_auc = sh_mm.auc_obj()._auc / lm.og_auc;
                        _p_var_imp[id] = lm.m_f_auc;
                    } else throw new MetricNotFoundExeption("Binomial model doesnt have auc " + _model._key);
                    break;
                case Multinomial:
                    if (!Double.isNaN(((ModelMetricsMultinomial) sh_mm)._logloss)) {
                        lm.m_f_logloss = ((ModelMetricsMultinomial) sh_mm).logloss() / lm.og_logloss;
                        _p_var_imp[id] = lm.m_f_logloss;
                    } else throw new MetricNotFoundExeption("Multinomial model doesnt have logloss " + _model._key);
                    break;
                default:
                    throw new MetricNotFoundExeption("Model Categoryt not supported for model" + _model._key);
            }
        } catch (MetricNotFoundExeption e) {
            System.err.println("ModelMetricCalculation threw an exception unchecked Classifier " + _model._key);
            e.printStackTrace();            
        } 
        return lm;
    }
    
    
    public TwoDimTable getFeatureImportance() {
        
        // put all the metrics in a class for structure
        LocalMetric pfi_m = new LocalMetric();
        
        removeResCol(); 
        setOriginalMetrics(pfi_m);
        
        int id = 0;
        for (int f = 0; f < _train_fr.numCols(); f++) 
        {
            // skip for response column
            if (_features[f].equals(_response_col))  continue;
            
            //shuffle values of feature
            Vec shuffled_feature = ShuffleVec.ShuffleTask.shuffle(_train_fr.vec(_features[f]));
            Vec og_feature = _train_fr.replace(f, shuffled_feature);

            Frame f_cp = new Frame(Key.<Frame>make(_model._key + "_shuffled." + f), _features, _train_fr.vecs());
            DKV.put(f_cp);

            // score the model again and compute diff
            Frame new_score = _model.score(_train_fr);

            // set and add new metrics
            pfi_m = addToFeatureToTable(pfi_m, id++, _train_fr);
            
            //return the original data
            _train_fr.replace(f, og_feature); // TODO use .add .remove methods to fix leaks (I presume)
            Frame f_og = new Frame(Key.<Frame>make(_model._key + "_original"), _features, _train_fr.vecs());
            DKV.put(f_og);

//            DKV.put(_train_fr); // "Caller must perform global update (DKV.put) on this updated frame"

            new_score.remove(); // clearing (some) leaks i think
            shuffled_feature.remove();
        }

        return ModelMetrics.calcVarImp(_p_var_imp, _features_wo_res);
    }
    
    public TwoDimTable oat (){ return oat(0); } // default is relative value of the TwoDimTable 
    
    public TwoDimTable oat(int type) {
        int r = 4; // set 4 to 10 value
        TwoDimTable[] morris_FI_arr = new TwoDimTable[r];
        
        // generate r tables of Feature importance differently shuffled
        for (int i = 0; i < r; i++) morris_FI_arr[i] = getFeatureImportance();

        System.out.println(morris_FI_arr[0]);


        double[] mean_FI = new double[_features_wo_res.length];

        // contians mean of the absolute value and standard deviation of each features importance
         double [][] _response = new double [_features_wo_res.length][2]; 

        // formula (add link to thesis or paper)
        for (int f = 0; f < _features_wo_res.length; f++) {
            double acc_abs = 0;
            double acc = 0;
            for (int i = 0; i < r; i++) {
                acc_abs += Math.abs((Double) morris_FI_arr[i].get(f, type));
                acc += (Double) morris_FI_arr[i].get(f, type);
            }
            _response[f][0] = (1.0 / r) * acc_abs; // for 2dTable
            mean_FI[f] = (1.0 / r) * acc; // for the upcoming calculation
        }

        // another formula 
        for (int f = 0; f < _features_wo_res.length; f++) {
            double inner = 0;
            for (int i = 0 ; i < r ; i++){
                inner += Math.pow((Double) morris_FI_arr[i].get(f, type) - mean_FI[f], 2);
            }
            _response[f][1] = Math.sqrt(1.0 / r * inner); // for 2dTable
        }
        
        String [] col_types = new String[2];
        String [] col_formats = new String[2];
        Arrays.fill(col_types, "double");
        Arrays.fill(col_formats, "%5f");
        
        
        return new TwoDimTable("Morris method analysis", null, _features_wo_res, new String [] {"Mean of the absolute value tsst", "standard deviation"},
                    col_types, col_formats, "Feature Importance", new String[_features_wo_res.length][], _response);

    }
}

