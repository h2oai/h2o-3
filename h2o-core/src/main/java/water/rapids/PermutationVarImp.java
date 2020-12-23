
package water.rapids;

import hex.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.TwoDimTable;
import water.util.VecUtils;

import java.util.*;

/**
 * Permutation Variable (feature) importance measures the increase in the prediction error of the model after permuting 
 * the variables's values, which breaks the relationship between the variables and the true outcome.
 * https://christophm.github.io/interpretable-ml-book/feature-importance.html
 *
 * Calculate permutation variables importance, by shuffling randomly each variables of the training Frame,
 * scoring the model with the newly created frame using One At a Time approach
 * and Morris method; creating TwoDimTable with relative, scaled, and percentage value
 *                             TwoDimTable with mean of the absolute value, and standard deviation of all features importance
 */

public class PermutationVarImp {

    public double[] _pVarImp; // permutation variables importance, (relative)

    private String _responseCol;

    private String[] _variables;
    private String[] _varsToShuffle;
    public String[] _metrics;

    /**
     * Stores the metric to be used (loss function) original values and j-th variables's score metric values  
     */
    public class LocalMetric{ // do it as a string to get the metric from the user

        LocalMetric(String metric)  { _metric = metric; }
        LocalMetric()               { _metric = "mse"; }
        public String _metric;
        double _ogMetric;
        double _variablesMetric;
    }

    public LocalMetric _varImpMetric;
    public Map<String, Double> _varImpMap;
    TwoDimTable _permutationVarImp;
    private Model _model;
    private Frame _inputFrame;

    /**
     * Constructor that stores the model, frame, response column, variable Strings and 
     * sets the allowed metrics
     * @param model trained model
     * @param fr training frame
     */
    public PermutationVarImp(Model model, Frame fr) {
        _model = model;
        _inputFrame = fr;
        _responseCol = _model._parms._response_column;
        _variables = _inputFrame.names();
        _metrics = new String[]{"r2", "mse", "rmse", "gini coefficient", "f1", "logloss", "auc"};
    }

    /**
     * Creates a new array of Strings without the response column and ignored columns
     */
    public void arrangeColsToShuffle(){
        List<String> list = new ArrayList<>(Arrays.asList(_inputFrame.names()));
        // remove ignored columns & response column
        if (_model._parms._ignored_columns != null)
            for (String s : _model._parms._ignored_columns) list.remove(s);
        list.remove(_responseCol);
        list.remove("weight_column");
        list.remove("fold_column");

        _varsToShuffle = list.toArray(new String[0]);

        // alloc permutation variable importance array 
        _pVarImp = new double[_varsToShuffle.length];
    }


    /**
     * Returns the metric (loss function) selected by the user (mse is default)
     * @throws IllegalArgumentException if metric could not be loaded 
     */
    private double getMetric(ModelMetrics mm) {
        double metric = Double.NaN;
        assert mm != null;
        ModelCategory mc = _model._output.getModelCategory();
        switch (_varImpMetric._metric){
            case "r2": metric = ((ModelMetricsSupervised)mm).r2();
                break;
            case "mse": metric = mm.mse();
                break;
            case "rmse": metric = mm.rmse();
                break;
            case "gini coefficient":
                if (mm.auc_obj() != null)
                    metric = mm.auc_obj()._gini;
                else throw new IllegalArgumentException("Model: " + _model._output.getModelCategory().name() + " doesn't have gini coefficient!");
                break;
            case  "logloss":
                if (mc == ModelCategory.Binomial)
                    metric = ((ModelMetricsBinomial)mm).logloss();
                else if (mc == ModelCategory.Multinomial)
                    metric = ((ModelMetricsMultinomial)mm).logloss();
                else throw new IllegalArgumentException(_model._output.getModelCategory().name() + " with " + _varImpMetric._metric + " not found ");
                break;
            case "auc":
                if (mc == ModelCategory.Binomial)
                    metric = ((ModelMetricsBinomial)mm).auc_obj()._auc;
                else throw new IllegalArgumentException(_model._output.getModelCategory().name() + " with " + _varImpMetric._metric + " not found ");
                break;
            default:
                throw new IllegalArgumentException("Metric not supported " + _varImpMetric._metric);
        }
        if(Double.isNaN(metric))
            throw new IllegalArgumentException("Model doesn't support the metric following metric " + _varImpMetric._metric);
        return metric;
    }

    /**
     * Set the metric upon training the model (original ~ Og)
     */
    public void setOgMetric() {
        _varImpMetric._ogMetric = getMetric(ModelMetrics.getFromDKV(_model, _model._parms.train()));
    }

    /**
     * Set the metric of the feature that is permuted
     */
    private void setVariablesMetric(int var){
        // subtract original metric with metric value obtained from shuffled variable 
        double shuffledVariablesMetric = getMetric(ModelMetrics.getFromDKV(_model, _inputFrame));
        _varImpMetric._variablesMetric = shuffledVariablesMetric - _varImpMetric._ogMetric;
        _pVarImp[var] = _varImpMetric._variablesMetric;
    }

    /**
     *  If the user specifies a metric store it to the LocalMetric class*
     * @return TwoDimTable of Permutation Feature Importance scores
     */
    public TwoDimTable getPermutationVarImp(String metric)  {
        if (ArrayUtils.find(_metrics, metric.toLowerCase()) == -1)
            throw new IllegalArgumentException("Permutation Variable Importance doesnt support " + metric + " for model " + _model._key );

        _varImpMetric = new LocalMetric(metric.toLowerCase());
        return permutationVarImportance();
    }

    /**
     * No metric was selected by the user so MSE will be used 
     * @return TwoDimTable of Permutation Feature Importance scores
     */
    public TwoDimTable getPermutationVarImp() {
        // put all the metrics in a class for structure
        _varImpMetric = new LocalMetric();
        assert _varImpMetric._metric.equals("mse");
        return permutationVarImportance();
    }

    /**
     * Check If the variable is in ignored parameters on the model
     * @param var Vec to be shuffled
     * @return true of variable is in ignored columns 
     */
    public boolean isIgnored(String var) {
        if (_model._parms._ignored_columns == null)
            return false;
        return ArrayUtils.find(_model._parms._ignored_columns, var) != -1;
    }

    /**
     * permute the feature's values breaking the relationship between the feature and the true outcome.
     * Then we score the model again and calculate the loss function, and creating a TwoDimTable.
     */
    public TwoDimTable permutationVarImportance(){
        arrangeColsToShuffle();
        setOgMetric(); // get the metric value from the model
        _varImpMap = new HashMap<>(_varsToShuffle.length);

        int id = 0;
        for (int f = 0; f < _inputFrame.numCols() ; f++)
        {
            if (_variables[f].equals(_responseCol) || isIgnored(_variables[f]))
                continue;
            //shuffle values of feature
            Vec shuffledFeature = VecUtils.ShuffleVec(_inputFrame.vec(_variables[f]), _inputFrame.vec(_variables[f]).makeCopy());
            Vec ogFeature = _inputFrame.replace(f, shuffledFeature);

            // score the model again and compute diff
            Frame newScore = _model.score(_inputFrame);

            // set and add new metrics ~ fills @param _p_var_imp needed for ModelMetrics.calcVarImp()
            setVariablesMetric(id);

            //return the original data
            _inputFrame.replace(f, ogFeature);

            // Create Map
            _varImpMap.put(_variables[f], _pVarImp[id++]);

            newScore.remove();
            shuffledFeature.remove();
        }

        // Create TwoDimTable having (Relative + Scaled + percentage) importance 
        _permutationVarImp = ModelMetrics.calcVarImp(_pVarImp, _varsToShuffle);
        return _permutationVarImp;
    }


    /**
     * Run PVI r times
     * Default is set to return the Relative value of the Permutation Feature Importance (PFI)
     * @return TwoDimTable with mean of the absolute value and standard deviation
     */

    public TwoDimTable oat() {
        int r = 7; // set 4 to 10

        // Using maps to not lose which Variable has Which score as to sometimes the ordering changes
        List<Map<String, Double>> listOfMaps = new ArrayList<Map<String, Double>>();
        Map<String, Double> meanOfInfluence = new HashMap<>();
        Map<String, Double> meanOfInfluenceAbsVal = new HashMap<>();

        // Generate r tables of Feature importance differently shuffled
        for (int i = 0; i < r; i++) {
            // Get permutationVarImp table
            TwoDimTable morrisFis = getPermutationVarImp();

            // On first iteration fill values
            if (meanOfInfluence.isEmpty()) {
                // _varImpMap "variable" -> Importance <String, Double> map
                meanOfInfluence.putAll(_varImpMap);
                // First iteration the maps are empty so just "copying" the values
                for (Map.Entry<String, Double> var : meanOfInfluence.entrySet()) {
                    meanOfInfluenceAbsVal.put(var.getKey(), Math.abs(var.getValue()));
                    meanOfInfluence.put(var.getKey(), Math.abs(var.getValue()));
                }
            }   else    {
                // The formula is: 1 / r * (Sum_{i=0}^r | PVI values |)  
                for (Map.Entry<String, Double> var : meanOfInfluence.entrySet()) {
                    // replace the value with the previous one + this iterations one
                    meanOfInfluence.replace(var.getKey(), var.getValue(), var.getValue() + _varImpMap.get(var.getKey()));
                    meanOfInfluenceAbsVal.replace(var.getKey(), var.getValue(), var.getValue() + Math.abs(_varImpMap.get(var.getKey())));
                }
            }
            // creating a list of VarImps to calculate standard deviation
            listOfMaps.add(_varImpMap);
        }

        // divide values of the Map by r
        for (Map.Entry<String, Double> var : meanOfInfluence.entrySet()) {
            meanOfInfluence.replace(var.getKey(), var.getValue(), var.getValue() / r);
        }
        for (Map.Entry<String, Double> var : meanOfInfluenceAbsVal.entrySet()) {
            meanOfInfluenceAbsVal.replace(var.getKey(), var.getValue(), var.getValue() / r);
        }

        // Calculate the meanOfInfluence of the absolute value of each feature's importance 
        Map<String, Double> standardDevFI = new HashMap<>();
        // Similrarly as before there's a sum hence the for loop for r
        for (int i = 0 ; i < r ; i++){
            // initialy just "copy" data
            if (standardDevFI.isEmpty()){
                for (Map.Entry<String, Double> var : listOfMaps.get(i).entrySet()) {
                    standardDevFI.put(var.getKey(), Math.pow(var.getValue() - meanOfInfluence.get(var.getKey()), 2));
                }
            } else {
                // Go through r calculations of PVI and apply the formula
                for (Map.Entry<String, Double> var : listOfMaps.get(i).entrySet()) {
                    standardDevFI.replace(var.getKey(), var.getValue(), var.getValue() + Math.pow(var.getValue() - meanOfInfluence.get(var.getKey()), 2));
                }
            }
        }

        // Divide every value by r
        for (Map.Entry<String, Double> var : standardDevFI.entrySet()) {
            standardDevFI.replace(var.getKey(), var.getValue(), Math.sqrt(var.getValue() / r));
        }

        // Contains the meanOfInfluence of the absolute value and standard deviation of each feature's importance, hence the [2]
        double [][] response = new double [meanOfInfluenceAbsVal.size()][2];

        // Create a matrix to fill the TwoDimTable
        int i = 0;
        for (String var : _varsToShuffle) {
            response[i][0] = meanOfInfluenceAbsVal.get(var);
            response[i++][1] = standardDevFI.get(var);
        }

        // Necessary to create the TwoDimTable
        String [] colTypes = new String[2];
        String [] colFormats = new String[2];
        Arrays.fill(colTypes, "double");
        Arrays.fill(colFormats, "%5f");

        return new TwoDimTable("One At a Time", null, _varsToShuffle, new String [] {"Mean of the absolute value", "standard deviation"},
                colTypes, colFormats, "Permutation Variable Importance", new String[_varsToShuffle.length][], response);
    }
}
