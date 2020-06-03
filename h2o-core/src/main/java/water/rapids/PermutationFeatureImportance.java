package water.rapids;

import hex.*;
//import hex.VarImp;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.*;


public class PermutationFeatureImportance /* extends VarImp */ {
    
    private Model _model;
    private Frame _train_fr; // original dataset used to train model
    private Frame _validate_dataset; // original dataset used to train model
    private Frame _predcited_frame;
    private String _responce_col;

    private boolean _data_in_holdOut_form; // do I have the data split in train and validation, if not spliting will happen

    HashMap<String, Double> _FI;
    IcedHashMap<String, Double> m_FI;

    TwoDimTable _OGmodel_reliance_scoring_history; // original model metrics
    
    int m_maxCols;
    int m_maxRows;
    TwoDimTable _PFI_metrics_table; // contains feature importance 'score' of each feature
    
    public TwoDimTable getTable() {return _PFI_metrics_table;}
    
    /**
     * When passing only the Model without Frame (will use models frame)
     * */
    public PermutationFeatureImportance(Model model) {
        _model = model;
        _train_fr = _model._parms._train.get();
        _data_in_holdOut_form = false;
        _validate_dataset = null;
        _responce_col = _model._parms._response_column;
        _OGmodel_reliance_scoring_history = _model._output._scoring_history;
    }

    public PermutationFeatureImportance(Model model, Frame data_set, Frame prediction_frame) {
        _model = model;
        _train_fr = data_set;
        _data_in_holdOut_form = false;
        _validate_dataset = null;
        _predcited_frame = prediction_frame;
        _responce_col = _model._parms._response_column;
        _OGmodel_reliance_scoring_history = _model._output._scoring_history;
    }
    
    public void getFrameAndScoredModel(Frame fr, Frame scored){
        _train_fr = fr;
        _predcited_frame = scored;
    }
    
    /**
     * Creates a `TwoDimTable` having the feature names as columns
     *                                    metrics       as rows     (based from `_model._output._training_metrics`)
     *                                    values filled with NaN    (initially)
     * */
    void initializeTwoDimTable(){
        System.out.print("Creating 2D table...");
        // remove response col from array of string to be added to TwoDimTable
        String[] features = _train_fr.names();
        if (features[0].equals(_responce_col)) {
            features = Arrays.copyOfRange(features, 1, features.length);
        } else { // scenario where the response_col isn't the first column 
            List<String> list = new ArrayList<String>(Arrays.asList(features));
            if (list.contains(_responce_col))   // paranoid if check
                list.remove(_responce_col);
            features = list.toArray(new String[0]);
        }
        // all values will be doubles
        String [] metric_values = new String[_train_fr.numCols() - 1]; // minus response col
        Arrays.fill(metric_values, "double");

        // printed as "%lf"
        String [] metric_values_f = new String[_train_fr.numCols() - 1]; // minus response col
        Arrays.fill(metric_values_f, "%lf");
        
        // `_training_metrics` table has these metrics
        String [] Rows = new String[]{"RMSE", "LogLoss", "AUC", "pr_auc", "Lift", "Classification Error"};
        
        // make sure lengths match
        assert ((features.length == metric_values.length) && (metric_values.length == metric_values_f.length));
        
        // create table
        _PFI_metrics_table = new TwoDimTable(
                "Permutation Feature Importance", "Feature f was x.x important on m metric of model", // + model_id
                Rows,
                features,
                metric_values,
                metric_values_f,
                "Metric");
        
        // set all to NaN
        for( int i=0; i<Rows.length; i++ ) {
            for (int j=0; j<features.length ; j++)
            _PFI_metrics_table.set(i, j, Double.NaN);
        }
        // will need when adding to TwoDimTable
        m_maxRows = Rows.length;
        m_maxCols = features.length;
        
//        System.out.print(_PFI_metrics_table);
    }
    
    void twoDimTableIntepreter(TwoDimTable table){
        String [] rows_str = table.getRowHeaders();
        String [] cols_str = table.getColHeaders();
        String descr = table.getTableDescription();
        System.out.print(table.toString());

        System.out.print("cols\n");
        for (int i = 0 ; i < cols_str.length ; i++){
            System.out.print("row $i = " + cols_str[i] + "\n");
        }
        
    }
    void AddToTwoDimTable(TwoDimTable table){
        System.out.println("After scoring...");
        String descr = table.getTableDescription();
        System.out.print(table.toString());

    }
    
    // store original metrics
    private static class LocalMetric{

        LocalMetric() {} //nothing to do
        /**
         * Original values of metrics 
         * */
        // for now only mse, logloss, auc (TODO decide if this is sufficent)
        double og_mse;
        double og_logloss;
        double og_auc;
        double og_pr_auc;
        double og_lift;
        double og_clsf_error;
        
        // metrics of feature f
        
        double m_f_mse; 
        double m_f_auc;
        double m_f_logloss;
    }
    /**
     *  Set the values of trained metrics to LocalMetric class
     * */
    private void setOriginalMetrics(LocalMetric m){
        ModelMetrics og_mm = _model._output._cross_validation_metrics != null ? _model._output._cross_validation_metrics : _model._output._validation_metrics != null ? _model._output._validation_metrics : _model._output._training_metrics;

//        ModelMetricsBinomial og_mm = (ModelMetricsBinomial)_model._output._training_metrics;
        if (og_mm.auc_obj() != null){// FIXME: checck properly if modell has auc 
            if (!Double.isNaN(og_mm.auc_obj()._auc))    m.og_auc = og_mm.auc_obj()._auc;
        }
        if (og_mm instanceof ModelMetricsBinomial) {
            if (!Double.isNaN(((ModelMetricsBinomial)og_mm)._logloss)) m.og_logloss = og_mm.mse();
        }
        if (!Double.isNaN(og_mm.mse()))             m.og_mse = og_mm.mse();
    }
     
    /**
     * Calculate loss function of scored model with shuffled feature and add to to the TwoDimTable 
     * */
    private LocalMetric addToFeatureToTable(int col, boolean skipped, LocalMetric lm){

//        ModelMetrics sh_mm = _model._output._cross_validation_metrics != null ? _model._output._cross_validation_metrics : _model._output._validation_metrics != null ? _model._output._validation_metrics : _model._output._training_metrics;
        ModelMetrics sh_mm = ModelMetrics.getFromDKV(_model, _model._parms.train());
        
//        ModelMetrics mm = hex.ModelMetrics.getFromDKV(_model, _model._parms.train());

        if (!Double.isNaN(sh_mm.mse())) {
            lm.m_f_mse = lm.og_mse / sh_mm.mse();
            _PFI_metrics_table.set(0, skipped ? col - 1 : col, lm.m_f_mse); // 0 is MSE
        }
        if (sh_mm instanceof ModelMetricsBinomial) {
            if (!Double.isNaN(((ModelMetricsBinomial) sh_mm)._logloss)) {
                lm.m_f_logloss = lm.og_logloss / ((ModelMetricsBinomial) sh_mm).logloss();
                _PFI_metrics_table.set(1, skipped ? col - 1 : col, lm.m_f_logloss); // 1 is LOGLOSS

            }
        }
        if (sh_mm.auc_obj() != null) { // FIXME: checck properly if model has auc 
            if (!Double.isNaN(sh_mm.auc_obj()._auc)) {
                lm.m_f_auc = lm.og_auc / sh_mm.auc_obj()._auc;
                _PFI_metrics_table.set(2, skipped ? col - 1 : col, lm.m_f_auc); // 2 is AUC
            }
        }
        return lm;
    }
    
    
    public HashMap<String, Double> getFeatureImportance() {
        if (!_data_in_holdOut_form) {
            //TODO split the data if needed
        }
        // to avoid OutOfRange of TwoDimTable excpetions
        boolean saw_response_col = false;
        
        // put all the metrics in a class for structure
        LocalMetric pfi_m = new LocalMetric();
        
        // create the TwoDimtable
        initializeTwoDimTable(); 
        
        String[] features = _train_fr.names();
        
        //TODO GET RID OF THIS
        _FI = new HashMap<String, Double>(_train_fr.vecs().length);
        
        setOriginalMetrics(pfi_m);
        
        for (int f = 0; f < _train_fr.numCols(); f++) 
        {
            // skip for response column
            if (features[f].equals(_responce_col)) {
                // when adding to TwoDimTable `f` is passed, since TwoDimTable has one column less I need to - 1 at some point
                saw_response_col = true;
                continue;
            }
            
            //shuffle values of feature
            
            Vec shuffled_feature = ShuffleVec.ShuffleTask.shuffle(_train_fr.vec(features[f]));
            Vec og_feature = _train_fr.replace(f, shuffled_feature);

            Frame f_cp = new Frame(Key.<Frame>make(_model._key + "_shuffled"), features, _train_fr.vecs());
            DKV.put(f_cp);

            // score the model again and compute diff
            Frame new_score = _model.score(_train_fr);

            // set and add new metrics
            pfi_m = addToFeatureToTable(f, saw_response_col, pfi_m);
            putValuesBasedOnModelTask(features[f], pfi_m);
            
            //return the original data
            _train_fr.replace(f, og_feature); // TODO use .add .remove methods to fix leaks (I presume)
            Frame f_og = new Frame(Key.<Frame>make(_model._key + "_original"), features, _train_fr.vecs());
            DKV.put(f_og);

//            DKV.put(_train_fr); // "Caller must perform global update (DKV.put) on this updated frame"

            new_score.remove(); // clearing (some) leaks i think
        }
        HashMap<String, Double> sorted_FI = sortHashMapByValues(_FI);
//        System.out.print(_PFI_metrics_table);
        return sortHashMapByValues(sorted_FI); // THIS IS NOT NEEDED, but leaving it for now
    }

    private void putValuesBasedOnModelTask(String feature, LocalMetric lmm) {
        if (_model._output.isBinomialClassifier())
            _FI.put(feature, lmm.m_f_logloss);  // testing purposes
        else if (_model._output.isMultinomialClassifier() || _model._output.isClassifier())
            _FI.put(feature, lmm.m_f_auc);  // testing purposes
        else    // regression
            _FI.put(feature, lmm.m_f_mse);  // testing purposes
            
    }

    // TODO GET RID OF THIS!
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
