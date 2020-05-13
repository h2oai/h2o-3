package water.rapids;

import hex.Model;
//import hex.VarImp;
import sun.java2d.jules.TileWorker;
import water.fvec.Frame;
import water.util.TwoDimTable;


public class FeatureImportance4BBM /* extends VarImp */ {
    
    Model _model;
    public Frame _ouput; //public for now
    TwoDimTable _orignal_dataset; // original dataset used to train model
    
    TwoDimTable permutation_dataset; // original dataset used to train model
    String algo_name; // what algorithm is it being called upon 

    TwoDimTable _scaled_FI;
    
    public FeatureImportance4BBM(Model model) {_model = model;}
    
    //TODO
    public void getWokringdataset() {}
    
    //TODO
    public void permutateColumns(TwoDimTable working_df) {}
    
    //TODO
    public void calculateValues() {}
    
    // dummy output for testing purposes
    public TwoDimTable Foo(){
        return _model._output._scoring_history;
    }
    
}
