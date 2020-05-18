package water.rapids;

import hex.Model;
//import hex.VarImp;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FrameUtils;
import water.util.MathUtils;
import water.util.TwoDimTable;
import water.util.VecUtils;


public class FeatureImportance4BBM /* extends VarImp */ {
    
    Model _model;
    public Frame _ouput; 
    Frame _train_fr; // original dataset used to train model
    Frame _validate_dataset; // original dataset used to train model
    Frame _predcited_frame; 
    
    boolean _data_in_holdOut_form; // do I have the data split in train and validation, if not spliting will happen
    
    String algo_name; 
    
    public FeatureImportance4BBM(Model model) {_model = model;}
    
    public FeatureImportance4BBM(Model model, Frame data_set, Frame prediction_frame) {
        _model = model;
        _train_fr = data_set;
        _data_in_holdOut_form = false;
        _validate_dataset = null;
        _predcited_frame = prediction_frame;
    }

    public FeatureImportance4BBM(Model model, Frame train_data_set, Frame validate_data_set, Frame prediction_frame) {
        _model = model;
        _train_fr = train_data_set;
        _validate_dataset = validate_data_set;
        _data_in_holdOut_form = true;
        _predcited_frame = prediction_frame;   
    }
    
    public void getFeatureImportance() {
        if(!_data_in_holdOut_form){
            //TODO split the data if needed
        }
        
        double sq_err = new MathUtils.SquareError().doAll(_train_fr.vecs()[1],_predcited_frame.vecs()[0])._sum;
        double init_mse = sq_err/_predcited_frame.numRows();
        sq_err = 0;
        
        double [] mse_all = new double [_train_fr.vecs().length];

        String [] features = _train_fr.names();
//        for (int f = 1; f < features.length; f++)//f = 0 is predicted col
//        {
            Vec shuffledColFr = Vec.makeVec(new VecUtils.shuffleVecVol2().doAll(_train_fr.vec(features[0]))._result, Vec.newKey());

            // score the model again and compute diff
        
            Frame new_score = _model.score(_train_fr);

            sq_err = new MathUtils.SquareError().doAll(_train_fr.vecs()[1],_predcited_frame.vecs()[0])._sum;
            mse_all[0] = init_mse - sq_err/new_score.numRows();
            
            //TODO revert  frame 
        
        // store a copy of the vector before shuffleing then append it, swap/remove with shuffled col
//        }

    }
    
}

