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
 */

public class PermutationVarImp {

    public double[] _pVarImp; // permutation variables importance, (relative)
    
    private String _responseCol; 
    
    private String[] _var; 
    private String[] _featuresWithNoResCol;
    public String[] _metrics;

    /**
     * Stores the metric to be used (loss function) original values and j-th variables's score metric values  
     */
    public class LocalMetric{ // do it as a string to get the metric from the user

        LocalMetric(String metric)  { mMetric = metric; }
        LocalMetric()               { mMetric = "mse"; } 
        String mMetric;
        double mOgMetric;
        double mVariablesMetric;
    }

    public LocalMetric _varImpMetric;
    public Map<String, Double> _varImpMap;
    TwoDimTable _permutationVarImp;
    private Model _model;
    private Frame _trainFr; 

    /**
     * When passing only the Model without Frame (will use models frame)
     */
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
        _var = _trainFr.names();
        _metrics = new String[]{"r2", "mse", "rmse", "gini coefficient", "f1", "logloss", "auc"};
    }
    
    /**
     * Creates a new array of Strings without the response column and ignored columns
     */
    public void removeResCol(){
        List<String> list = new ArrayList<String>(Arrays.asList(_trainFr.names()));
        // remove ignored columns & response column
        if (_model._parms._ignored_columns != null)
            for (String s : _model._parms._ignored_columns) list.remove(s);
        list.remove(_responseCol);
        _featuresWithNoResCol = list.toArray(new String[0]);
        
        // alloc permutation variable importance array 
        _pVarImp = new double[_featuresWithNoResCol.length];
    }

    
    /**
     * Returns the metric (loss function) selected by the user (mse is default)
     * @throws MetricNotFoundException if metric could not be loaded 
     */
    private double getMetric(ModelMetrics mm) throws MetricNotFoundException {
        double metric = Double.NaN;
        assert mm != null;
        try{
            switch (_varImpMetric.mMetric){
                case "r2":  metric = _model.r2();
                    break;
                case "mse": metric = mm.mse();
                    break;
                case "rmse": metric = mm.rmse();
                    break;
                case "gini coefficient": metric = mm.auc_obj()._gini;
                    break;
                case  "logloss": metric = _model.logloss();
                    ModelCategory mc = _model._output.getModelCategory();
                    if (mc == ModelCategory.Binomial)
                        metric = ((ModelMetricsBinomial)mm).logloss();
                    else if (mc == ModelCategory.Multinomial)
                        metric = ((ModelMetricsMultinomial)mm).logloss();
                    else throw new MetricNotFoundException( _model._output.getModelCategory().name() + " with " + _varImpMetric.mMetric + " not found ");
                    break; 
                case "auc": metric = mm.auc_obj()._auc;
                    break;
                default:
                    throw new MetricNotFoundException("Metric not supported " + _varImpMetric.mMetric);
            }
        } catch (MetricNotFoundException |NullPointerException e){ 
            System.err.println("ModelMetricCalculation  " + _model._key);
            e.printStackTrace();
        }
        if(Double.isNaN(metric))
            throw new MetricNotFoundException("Model doesn't support the metric following metric " + _varImpMetric.mMetric);
        return metric;
    }
    
    /**
     * Set the loss function upon training the model (original ~ Og)
     */
    public void setOgModelMetric() {
        try{
            _varImpMetric.mOgMetric = getMetric(ModelMetrics.getFromDKV(_model, _model._parms.train()));
        } catch (MetricNotFoundException e){
            System.err.println("Metric " + _varImpMetric.mMetric + " not supported for :" + _model._key);
            e.printStackTrace();
        }
    }
    
    /**
     * Set the loss function of the feature that's permutated
     */
    private void setVariablesMetric(int var){
        try{
            // divide original metric (upon training) with metric value obtained from shuffled variable 
            _varImpMetric.mVariablesMetric = getMetric(ModelMetrics.getFromDKV(_model, _trainFr)) - _varImpMetric.mOgMetric;
        } catch (MetricNotFoundException e){
            System.err.println("Metric " + _varImpMetric.mMetric + " not supported for :" + _model._key);
            e.printStackTrace();
        }
        _pVarImp[var] = _varImpMetric.mVariablesMetric;
    }

    /**
     *  If the user specifies a metric store it to the LocalMetric class otherwise uses the metric based on the model*
     */
    public TwoDimTable getPermutationVarImp(String metric)  {
        try {
            if (!Arrays.asList(_metrics).contains(metric.toLowerCase()))
                throw new MetricNotFoundException("Permutation Variable Importance doesnt support " + metric + " for model " + _model._key );
            } catch (MetricNotFoundException e) {
            System.err.println("Metric " + _varImpMetric.mMetric + " not supported for :" + _model._key);
            e.printStackTrace();
        }

        _varImpMetric = new LocalMetric(metric.toLowerCase());
        return PermutationVarImportance();
    }
    public TwoDimTable getPermutationVarImp() {
        // put all the metrics in a class for structure
        _varImpMetric = new LocalMetric();
        assert _varImpMetric.mMetric.equals("mse");  
        return PermutationVarImportance();
    }

    /**
     * Check if variable was set to be ignored
     * @param ignoredVars ignored variables
     * @param var variable to be shuffled
     */
    public static boolean isIgnored(String[] ignoredVars, String var) {
        return Arrays.asList(ignoredVars).contains(var);
    }
    
    /**
     * permute the feature's values breaking the relationship between the feature and the true outcome.
     * Then we score the model again and calculate the loss function, and creating a TwoDimTable.
     */
    public TwoDimTable PermutationVarImportance(){
        removeResCol();
        setOgModelMetric(); // get the metric value from the model
         _varImpMap = new HashMap<>(_featuresWithNoResCol.length);
        
//        Vec vec = _trainFr.remove(_responseCol);
        
        int id = 0;
        for (int f = 0; f < _trainFr.numCols(); f++)
        {
            // skip for response column
            if (_var[f].equals(_responseCol) ||  
                    (_model._parms._ignored_columns == null ? 
                    false : 
                    isIgnored(_model._parms._ignored_columns, _var[f]))) {
                continue;
            }

            //shuffle values of feature
            Vec shuffled_feature = VecUtils.ShuffleVec(_trainFr.vec(_var[f]), _trainFr.vec(_var[f]).makeCopy());
            Vec og_feature = _trainFr.replace(f, shuffled_feature);

            // score the model again and compute diff
            Frame new_score = _model.score(_trainFr);

            // set and add new metrics ~ fills @param _p_var_imp needed for ModelMetrics.calcVarImp()
            setVariablesMetric(id);


            //return the original data
            _trainFr.replace(f, og_feature); 

            // Create Map
            _varImpMap.put(_var[f], _pVarImp[id++]);
            
            new_score.remove();
            shuffled_feature.remove();
        }
        // Create TwoDimTable having (Relative + Scaled + percentage) importance 
        _permutationVarImp = ModelMetrics.calcVarImp(_pVarImp, _featuresWithNoResCol);
        return _permutationVarImp;
    }

    /**
     * Default is set to return the Relative value of the Permutation Feature Importance (PFI)
     */
    public TwoDimTable oat(){ return oat(0); } 
    
    /**
     * @param type {0,1,2}
     * type 0: Relative value of PFI
     * type 1: Scaled value of PFI
     * type 2: Percentage value of PFI
     */
    public TwoDimTable oat(int type) {
        int r = 4; // set 4 to 10 
        TwoDimTable[] morris_FI_arr = new TwoDimTable[r];
        
        // Generate r tables of Feature importance differently shuffled
        for (int i = 0; i < r; i++) {
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

