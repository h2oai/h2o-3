package ai.h2o.targetencoding;

import hex.Model;
import hex.ModelPreprocessor;
import water.DKV;
import water.Key;
import water.fvec.Frame;

import java.util.Objects;

import static ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy.*;

public class TargetEncoderPreprocessor extends ModelPreprocessor {
    
    private TargetEncoderModel _targetEncoder;

    public TargetEncoderPreprocessor(TargetEncoderModel targetEncoder) {
        super(Key.make(Objects.toString(targetEncoder._key)+"_preprocessor"));
        this._targetEncoder = targetEncoder;
        DKV.put(this);
    }

    @Override
    public Frame processTrain(Frame fr, Model.Parameters params) {
        if (params._is_cv_model && _targetEncoder._parms._data_leakage_handling == KFold) {
            return _targetEncoder.transformTraining(fr, params._cv_fold);
        } else {
            return _targetEncoder.transformTraining(fr);
        }
    }

    @Override
    public Frame processValid(Frame fr, Model.Parameters params) {
        if (params._is_cv_model && _targetEncoder._parms._data_leakage_handling == KFold) {
            return _targetEncoder.transformTraining(fr);
        } else {
            return _targetEncoder.transform(fr);
        }
    }

    @Override
    public Frame processScoring(Frame fr, Model model) {
        return _targetEncoder.transform(fr);
    }
}
