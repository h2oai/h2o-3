package water.rapids;

import hex.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.TwoDimTable;
import water.util.VecUtils;

import java.util.*;

/**
 * Permutation Variable (feature) importance measures the increase in the prediction error of the model after we permuted 
 * the variables's values, which breaks the relationship between the variables and the true outcome.
 * https://christophm.github.io/interpretable-ml-book/feature-importance.html
 *
 * Calculate permutation variables importance, by shuffling randomly each variables of the passed model
 * then scoring the model with the newly created frame using One At a Time approach
 * and Morris method; creating TwoDimTable with relative, scaled, and percentage value
 *                             TwoDimTable with mean of the absolute value, and standard deviation of all features importance
 *                             
 * */

public class PermutationVarImp {

    public double[] _pVarImp; // permutation variables (feature) importance, relative value      
    
    private String _responseCol; 
    
    private String[] _features; 
    private String[] _featuresWithNoResCol; // features without response col
    public String[] _metrics;

    /**
     * Stores the metric to be used (loss function) original values and j-th variables's score metric values  
     * */
    public class LocalMetric{ // do it as a string to get the metric from the user

        LocalMetric(String metric) {    m_selectedMetric = metric;  }
        LocalMetric() {    m_selectedMetric = "mse";  } 
        String m_selectedMetric;
        double m_ogMetric;
        double m_variablesMetric;
    }

    public LocalMetric _variImpMetric;
    TwoDimTable _permutationVarImp;
    private Model _model;
    private Frame _trainFr; // original dataset used to train model

    /**
     * When passing only the Model without Frame (will use models frame)
     * */
    public PermutationVarImp(Model model) {
        _model = model;
        _trainFr = _model._parms._train.get();
        init();
    }

    public PermutationVarImp(Model model, Frame data_set) {
        _model = model;
        _trainFr = data_set;
        init();
    }
    
    void init(){
        _responseCol = _model._parms._response_column;
        _features = _trainFr.names();

        // regression: mse, R2, rmse, rmsele , mae
        // classification gini, absolute_mcc, logloss, AUC, AUC_PR 
        _metrics = new String[]{"r2", "mse", "rmse", "gini coefficient", "f1", "logloss", "auc"};
    }
    public String [] get_features_wo_res_test(){return _featuresWithNoResCol;}
    
    /**
     * Creates a new array of Strings without the response column
     * */
    public void removeResCol(){
        // remove response col from array of string to be added to TwoDimTable
        _featuresWithNoResCol = _trainFr.names();
        if (_featuresWithNoResCol[0].equals(_responseCol)) {
            _featuresWithNoResCol = Arrays.copyOfRange(_featuresWithNoResCol, 1, _featuresWithNoResCol.length);
        } else { // scenario where the response_col isn't the first column 
            List<String> list = new ArrayList<String>(Arrays.asList(_featuresWithNoResCol));
            if (list.contains(_responseCol))   // paranoid if check
                list.remove(_responseCol);
            _featuresWithNoResCol = list.toArray(new String[0]);
        }
        _pVarImp = new double[_featuresWithNoResCol.length]; 
        
//        _train_fr.remove(_model._output.responseName());
//        _train_fr._names
    }

    
    private double getMetric() throws MetricNotFoundExeption{
//        ModelMetrics og_mm = ModelMetrics.getFromDKV(_model, _model._parms.train());
        ModelMetrics og_mm = ModelMetrics.getFromDKV(_model, _trainFr);
        double metric = Double.NaN;
        assert og_mm != null;
        try{
            switch (_variImpMetric.m_selectedMetric){
                case "r2":  metric = _model.r2();
                    break;
                case "mse": metric = og_mm.mse();
                    break;
                case "rmse": metric = og_mm.rmse();
                    break;
                case "gini coefficient": metric = og_mm.auc_obj()._gini;
                    break;
                case  "logloss": metric = _model.logloss();
                    break;
                case "auc": metric = og_mm.auc_obj()._auc;
                default:
                    throw new MetricNotFoundExeption("there was a metric not handled well! " + _variImpMetric.m_selectedMetric);
            }
        } catch (NullPointerException e){ // or nullptrexpection
            System.err.println("ModelMetricCalculation threw an exception unchecked Classifier " + _model._key);
            e.printStackTrace();
        }
        if(Double.isNaN(metric))
            throw new MetricNotFoundExeption("Model doesn't support the metric following metric " + _variImpMetric.m_selectedMetric);
        return metric;
    }
    
    private void setOgModelMetric() {
        try{
            _variImpMetric.m_ogMetric = getMetric();
        } catch (MetricNotFoundExeption e){
            System.err.println("Metric " + _variImpMetric.m_selectedMetric + " not supported for ! " + _model._key);
            e.printStackTrace();
        }
    }

    private void setVariablesMetric(int var){
        try{
            _variImpMetric.m_variablesMetric = _variImpMetric.m_ogMetric / getMetric();
        } catch (MetricNotFoundExeption e){
            System.err.println("Metric " + _variImpMetric.m_selectedMetric + " not supported for ! " + _model._key);
            e.printStackTrace();
        }
        _pVarImp[var] = _variImpMetric.m_variablesMetric;
    }


    /** If the user specifies a metric store it to the LocalMetric class otherwise uses the metric based on the model*/
    public TwoDimTable getPermutationVarImp(String metric)  {
        try {
            if (!Arrays.asList(_metrics).contains(metric.toLowerCase()))
                throw new MetricNotFoundExeption("Permutation Variable Importance doesnt support " + metric + " for model " + _model._key );
            } catch (MetricNotFoundExeption e) {
                e.printStackTrace();
        }

        _variImpMetric = new LocalMetric(metric.toLowerCase());
        return PermutationVarimp();
    }
    public TwoDimTable getPermutationVarImp() {
        // put all the metrics in a class for structure
        _variImpMetric = new LocalMetric();
        assert _variImpMetric.m_selectedMetric == "mse";  
        return PermutationVarimp();
    }

    /**
     * permute the feature's values breaking the relationship between the feature and the true outcome.
     * Then we score the model again and calculate the loss function, and creating a TwoDimTable.
     * */
    public TwoDimTable PermutationVarimp(){
        removeResCol();
        setOgModelMetric();
        
        int id = 0;
        for (int f = 0; f < _trainFr.numCols(); f++)
        {
            // skip for response column
            if (_features[f].equals(_responseCol))  continue;

            //shuffle values of feature
            Vec shuffled_feature = VecUtils.ShuffleVec(_trainFr.vec(_features[f]), _trainFr.vec(_features[f]).makeCopy());
            Vec og_feature = _trainFr.replace(f, shuffled_feature);

            // score the model again and compute diff
            Frame new_score = _model.score(_trainFr);

            // set and add new metrics ~ fills @param _p_var_imp needed for ModelMetrics.calcVarImp()
            setVariablesMetric(id++);

            //return the original data
            _trainFr.replace(f, og_feature); 

            new_score.remove();
            shuffled_feature.remove();
        }
        _permutationVarImp = ModelMetrics.calcVarImp(_pVarImp, _featuresWithNoResCol);
        return _permutationVarImp;
    }
    
    public Map <String, Double> toMapScaled(){
        assert _permutationVarImp != null;
        Map<String, Double> varImpMap = new HashMap<>(_pVarImp.length);
        for (int i = 0; i < _pVarImp.length; i++) {
            varImpMap.put(_featuresWithNoResCol[i], (Double)_permutationVarImp.get(i, 1)); // col 1 is scaled values
        }
        return varImpMap;
    }


    /**
     * Default is set to return the Relative value of the Permutation Feature Importance (PFI)
     * */
    public TwoDimTable oat(){ return oat(0); } 
    
    /**
     * @param type {0,1,2}
     * type 0: Relative value of PFI
     * type 1: Scaled value of PFI
     * type 2: Percentage value of PFI
     * */
    
    public TwoDimTable oat(int type) {
        int r = 4; // set 4 to 10 
        TwoDimTable[] morris_FI_arr = new TwoDimTable[r];
        
        // Generate r tables of Feature importance differently shuffled
        for (int i = 0; i < r; i++) 
        {
            morris_FI_arr[i] = getPermutationVarImp();
            System.out.println(morris_FI_arr[i]);
        }

        double[] mean_FI = new double[_featuresWithNoResCol.length];

        // Contains the mean of the absolute value and standard deviation of each feature's importance, hence the [2]
        double [][] response = new double [_featuresWithNoResCol.length][2]; 

        // Calculate the mean of the absolute value of each feature's importance (add link to thesis or paper)
        for (int f = 0; f < _featuresWithNoResCol.length; f++) {
            double acc_abs = 0;
            double acc = 0;
            for (int i = 0; i < r; i++) {
                acc_abs += Math.abs((Double) morris_FI_arr[i].get(f, type));
                acc += (Double) morris_FI_arr[i].get(f, type);
            }
            response[f][0] = (1.0 / r) * acc_abs; // for TwoDimTable column 0
            mean_FI[f] = (1.0 / r) * acc; // for the upcoming calculation
        }

        // Calculate the standard deviation of each feature's importance 
        for (int f = 0; f < _featuresWithNoResCol.length; f++) {
            double inner = 0;
            for (int i = 0 ; i < r ; i++){
                inner += Math.pow((Double) morris_FI_arr[i].get(f, type) - mean_FI[f], 2);
            }
            response[f][1] = Math.sqrt(1.0 / r * inner); // for TwoDimTable column 1
        }
        
        // Necessary to create the TwoDimTable
        String [] col_types = new String[2];
        String [] col_formats = new String[2];
        Arrays.fill(col_types, "double");
        Arrays.fill(col_formats, "%5f");
        
        
        return new TwoDimTable("One At a Time", null, _featuresWithNoResCol, new String [] {"Mean of the absolute value", "standard deviation"},
                    col_types, col_formats, "Permutation Variable Importance", new String[_featuresWithNoResCol.length][], response);

    }
    
}

