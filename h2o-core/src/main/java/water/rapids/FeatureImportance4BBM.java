package water.rapids;

import hex.Model;
//import hex.VarImp;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FrameUtils;
import water.util.MathUtils;
import water.util.TwoDimTable;
import water.util.VecUtils;

import java.util.Arrays;


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
    
    
    /* maybe afeter creating the fld put it in the DKV */
    public void getFeatureImportance() {
        if(!_data_in_holdOut_form){
            //TODO split the data if needed
        }
//            Frame deepCopy(String keyName)
        String [] features = _train_fr.names();
        
        // initial criterion
        double sq_err = new MathUtils.SquareError().doAll(_train_fr.vecs()[1],_predcited_frame.vecs()[0])._sum;
        double init_mse = sq_err/_predcited_frame.numRows();

        // create a copy of original training data
        Frame _shuffled_train_fr = _train_fr.deepCopy(Key.make().toString());
        DKV.put(_shuffled_train_fr);
        _shuffled_train_fr.vec(0).setNA(1);

        double [] mse_all = new double [_train_fr.vecs().length];
//        ShuffleVec shf = new ShuffleVec(_shuffled_train_fr);          I see it as usefull for later

        for (int f = 1; f < _train_fr.numCols(); f++)//f = 0 is predicted col
        {
            //permuate vector
            ShuffleVec.ShuffleTask.shuffle(_shuffled_train_fr.vec(features[f]));

            // score the model again and compute diff
            Frame new_score = _model.score(_shuffled_train_fr);

            // calculate criterion usnig MSE for now TODO calculate critorion based on choice
            sq_err = new MathUtils.SquareError().doAll(_shuffled_train_fr.vecs()[1],_predcited_frame.vecs()[0])._sum;
            
            // store results in array TODO Use hashmap string (feature) to double (calculation)
            mse_all[f] = init_mse - sq_err/_predcited_frame.numRows();

            /* TODO create a swaping function */
//            swap_vecs(_train_fr.vec(f), _shuffled_train_fr.vec(f), features[f]);
            
            // lazy add/remove
            _shuffled_train_fr.remove(features[f]);
            _shuffled_train_fr.add(features[f], _train_fr.vec(features[f]));


        }
    }
    public void swap_vecs(Vec src, Vec dst, String feature_name){

        if( !src.group().equals(dst.group()) && !Arrays.equals(src.espc(),dst.espc()) )
            throw new IllegalArgumentException("Vector groups differs - swaping vec'"+ feature_name);
        if( _train_fr.numRows() != dst.length() )
            throw new IllegalArgumentException("Vector lengths differ - swaping vec '"+feature_name);
        System.arraycopy(src,0,dst,1, (int) _train_fr.numRows()); // careful typecasting long to int!!!! FIXME

/*
        System.arraycopy(_names,0,names,0,i);
        System.arraycopy(_vecs,0,vecs,0,i);
        System.arraycopy(_keys,0,keys,0,i);
        names[i] = name;
        vecs[i] = vec;
        keys[i] = vec._key;
        System.arraycopy(_names,i,names,i+1,_names.length-i);
        System.arraycopy(_vecs,i,vecs,i+1,_vecs.length-i);
        System.arraycopy(_keys,i,keys,i+1,_keys.length-i);
        _vecs = vecs;
        setNames(names);
        _keys = keys;
*/


    }
    
}

