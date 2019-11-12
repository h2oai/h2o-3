from __future__ import print_function
import sys, os, time, warnings
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.targetencoder import TargetEncoder

"""
This test is used to check Rapids wrapper for java TargetEncoder
"""

def test_target_encoding_parameters():
    print("Check arguments to TargetEncoder class")
    targetEncoder = TargetEncoder(x=["teColumn1"])

    assert targetEncoder._teColumns == ["teColumn1"]


def test_target_encoding_fit_method():
    print("Check fit method of the TargetEncoder class")
    targetColumnName = "survived"
    foldColumnName = "kfold_column" # it is strange that we can't set name for generated kfold

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  fold_column= foldColumnName, blended_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    encodingMap = targetEncoder.fit(frame=trainingFrame)
    assert encodingMap.map_keys['string'] == teColumns
    assert encodingMap.frames[0]['num_rows'] == 583


def test_target_encoding_transform_kfold():
    print("Check transform method (kfold strategy) of the TargetEncoder class")
    targetColumnName = "survived"
    foldColumnName = "kfold_column" # it is strange that we can't set name for generated kfold

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  fold_column= foldColumnName, blended_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    targetEncoder.fit(trainingFrame)

    encodedFrame = targetEncoder.transform(frame=trainingFrame, holdout_type="kfold", seed=1234)

    teColumnsEncoded = list(map(lambda x: x+"_te", teColumns))
    frameWithEncodingsOnly = encodedFrame[teColumnsEncoded]
    assert frameWithEncodingsOnly.ncols == 3


def test_target_encoding_transform_loo():
    print("Check transform (loo strategy) of the TargetEncoder class")
    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  fold_column='', blended_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()

    targetEncoder.fit(frame=trainingFrame)

    encodedFrame = targetEncoder.transform(frame=trainingFrame, holdout_type="loo", seed=1234)

    teColumnsEncoded = list(map(lambda x: x+"_te", teColumns))
    frameWithEncodingsOnly = encodedFrame[teColumnsEncoded]
    assert frameWithEncodingsOnly.ncols == 3


def test_target_encoding_transform_none():
    print("Check transform (none strategy) of the TargetEncoder class")
    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  blended_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()

    targetEncoder.fit(frame=trainingFrame)

    encodedFrame = targetEncoder.transform(frame=trainingFrame, holdout_type="none", noise=0, seed=1234)

    teColumnsEncoded = list(map(lambda x: x+"_te", teColumns))
    frameWithEncodingsOnly = encodedFrame[teColumnsEncoded]
    assert frameWithEncodingsOnly.ncols == 3

def test_target_encoding_transform_none_blending():
    print("Check none strategy with and without blending")
    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    teColumnsEncoded = list(map(lambda x: x+"_te", teColumns))
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)
    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    
    targetEncoderWithBlending = TargetEncoder(x= teColumns, y= targetColumnName,
                                              blended_avg= True, inflection_point = 3, smoothing = 1)
    
    targetEncoderWithBlending.fit(frame=trainingFrame)

    encodedFrameWithBlending = targetEncoderWithBlending.transform(frame=trainingFrame, holdout_type="none", noise=0, seed=1234)

    frameWithBlendedEncodingsOnly = encodedFrameWithBlending[teColumnsEncoded]

    targetEncoderWithoutBlending = TargetEncoder(x= teColumns, y= targetColumnName,
                                                 blended_avg= False, inflection_point = 3, smoothing = 1)

    targetEncoderWithoutBlending.fit(frame=trainingFrame)

    encodedFrameWithoutBlending = targetEncoderWithoutBlending.transform(frame=trainingFrame, holdout_type="none", noise=0, seed=1234)
    encodedFrameWithoutBlendingOnly = encodedFrameWithoutBlending[teColumnsEncoded]

    try:
        pyunit_utils.compare_frames(frameWithBlendedEncodingsOnly, encodedFrameWithoutBlendingOnly, 10, tol_time=0, tol_numeric=1e-6)
        assert False
    except AssertionError:
        print('Good, encodings are different as expected. Hopefully because of the blending.')


def test_target_encoding_seed_is_working():
    print("Check that seed is applied when we use noise. Noise is set to the same values. Only seed is different.")
    noiseTest = 0.02

    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    teColumnsEncoded = list(map(lambda x: x+"_te", teColumns))
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)
    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()

    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  blended_avg= True, inflection_point = 3, smoothing = 1)

    targetEncoder.fit(frame=trainingFrame)

    encodedFrame = targetEncoder.transform(frame=trainingFrame, holdout_type="loo", noise=noiseTest, seed=1234)

    encodingsOnly = encodedFrame[teColumnsEncoded]

    # Second transformation with the same seed 1234
    encodedFrame2 = targetEncoder.transform(frame=trainingFrame, holdout_type="loo", noise=noiseTest, seed=1234)
    encodingsOnly2 = encodedFrame2[teColumnsEncoded]

    # Third  transformation with another seed 1235
    encodedFrame3 = targetEncoder.transform(frame=trainingFrame, holdout_type="loo", noise=noiseTest, seed=1235)
    encodingsOnly3 = encodedFrame3[teColumnsEncoded]

    # Comparing results
    # First two encodings should be equal
    assert pyunit_utils.compare_frames(encodingsOnly, encodingsOnly2, 10, tol_time=0, tol_numeric=1e-6)
    # Third encoding should be different from the first two ones
    try:
        pyunit_utils.compare_frames(encodingsOnly, encodingsOnly3, 10, tol_time=0, tol_numeric=1e-6)
        assert False
    except AssertionError:
        print('Good, encodings are different for different seeds as expected. Seed is working.')


def test_target_encoding_default_noise_is_applied():
    print("Check that seed is applied when we use noise. Noise is set to the same values. Only seed is different.")

    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    teColumnsEncoded = list(map(lambda x: x+"_te", teColumns))
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)
    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()

    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  blended_avg= True, inflection_point = 3, smoothing = 1)

    targetEncoder.fit(frame=trainingFrame)

    seedTest = 1234
    encodedFrame = targetEncoder.transform(frame=trainingFrame, holdout_type="loo", noise=0.0, seed=seedTest)

    encodingsOnly = encodedFrame[teColumnsEncoded]

    # Second transformation without specifying noise. Default will be applied.
    encodedFrame2 = targetEncoder.transform(frame=trainingFrame, holdout_type="loo", seed=seedTest)
    encodingsOnly2 = encodedFrame2[teColumnsEncoded]

    # Third  transformation with zero noise
    encodedFrame3 = targetEncoder.transform(frame=trainingFrame, holdout_type="loo", noise=0.0, seed=seedTest)
    encodingsOnly3 = encodedFrame3[teColumnsEncoded]

    # Comparing results
    # Third encoding should be equal to the first one since no noise is applied in both cases
    assert pyunit_utils.compare_frames(encodingsOnly, encodingsOnly3, 10, tol_time=0, tol_numeric=1e-6)
    # First two encodings should be different since default noise will be applied to the second transformation
    try:
        pyunit_utils.compare_frames(encodingsOnly, encodingsOnly2, 10, tol_time=0, tol_numeric=1e-6)
        assert False
    except AssertionError:
        print('Good, encodings are different as expected. Default noise is working')

def test_ability_to_pass_column_parameters_as_indexes():
    print("Check that we can pass indices for specifying columns")
    targetColumnIdx = 1 
    targetColumnName = "survived"
    foldColumnIdx = 14
    foldColumnName = "kfold_column" 

    teColumns = [13] # 13 stands for `home.dest`
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnIdx,
                                  fold_column= foldColumnIdx, blended_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)
    
    encodingMap = targetEncoder.fit(frame=trainingFrame)
    assert encodingMap.map_keys['string'] == ["home.dest"]
    assert encodingMap.frames[0]['num_rows'] == 583


def test_that_both_deprecated_and_new_parameters_are_working_together():
    print("Check that both deprecated and new parameters are working together")
    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName, blending_avg= False)
    targetEncoderNewConstructor = TargetEncoder(x= teColumns, y= targetColumnName, blended_avg= False)
    targetEncoderDefault = TargetEncoder(x= teColumns, y= targetColumnName)

    assert targetEncoder._blending == False
    assert targetEncoder._blending == targetEncoderNewConstructor._blending
    assert targetEncoderDefault._blending == True

    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter("always")
        TargetEncoder(x= teColumns, y= targetColumnName, blending_avg= False)
        assert len(w) == 1
        assert issubclass(w[-1].category, DeprecationWarning)
        assert "Parameter blending_avg is deprecated; use blended_avg instead" == str(w[-1].message)


def test_teColumns_parameter_as_single_column_name():
    print("Check fit method can accept non-array single column to encode")
    targetColumnName = "survived"
    foldColumnName = "kfold_column" # it is strange that we can't set name for generated kfold

    teColumns = "home.dest"
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  fold_column= foldColumnName, blending_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    encodingMap = targetEncoder.fit(frame=trainingFrame)
    assert encodingMap.map_keys['string'] == [teColumns]
    assert encodingMap.frames[0]['num_rows'] == 583
    
    trainingFrame = targetEncoder.transform(trainingFrame, holdout_type="kfold", seed=1234)
    assert "home.dest_te" in trainingFrame.columns


def test_teColumns_parameter_as_single_column_index():
    print("Check fit method can accept non-array single column to encode")
    targetColumnName = "survived"
    foldColumnName = "kfold_column" # it is strange that we can't set name for generated kfold

    teColumns = 13 # stands for "home.dest" column
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  fold_column= foldColumnName, blending_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    encodingMap = targetEncoder.fit(frame=trainingFrame)
    assert encodingMap.map_keys['string'] == [trainingFrame.columns[teColumns]]
    trainingFrame = targetEncoder.transform(trainingFrame, holdout_type="kfold", seed=1234)
    assert "home.dest_te" in trainingFrame.columns


def pubdev_6474_test_more_than_two_columns_to_encode_case():
    import pandas as pd
    import random

    runs = 10
    seeds = random.sample(range(1, 10000), runs)
    for current_seed in seeds:
        df = pd.DataFrame({
            'x_0': ['a'] * 5 + ['b'] * 5,
            'x_1': ['c'] * 9 + ['d'] * 1,
            'x_2': ['e'] * 2 + ['f'] * 8,
            'x_3': ['h'] * 4 + ['i'] * 6,
            'x_4': ['g'] * 7 + ['k'] * 3,
            'x_5': ['l'] * 1 + ['m'] * 9,
            'y_0': [1, 1, 1, 1, 0, 1, 0, 0, 0, 0]
        })
        
        hf = h2o.H2OFrame(df)
        hf['cv_fold_te'] = hf.kfold_column(n_folds=2, seed=current_seed)
        hf['y_0'] = hf['y_0'].asfactor()
    
        full_features = ['x_0','x_1','x_2', 'x_3', 'x_4', 'x_5']
        target_encoder = TargetEncoder(x=full_features, y='y_0', fold_column = 'cv_fold_te')
        target_encoder.fit(hf)
        hf = target_encoder.transform(frame=hf, holdout_type='kfold', seed=current_seed, noise=0.0)


def test_blending_params_are_within_valid_range():
    print("Check validation for blending hyperparameters")
    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)
    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()

    try:
        TargetEncoder(x= teColumns, y= targetColumnName, blended_avg= True, inflection_point = 0, smoothing = 1)
        assert False
    except ValueError:
        print('Good, exception was thrown as `inflection_point` is outside of the valid range')

    try:
        TargetEncoder(x= teColumns, y= targetColumnName, blended_avg= True, inflection_point = 1, smoothing = 0)
        assert False
    except ValueError:
        print('Good, exception was thrown as expected')


def test_that_error_will_be_thrown_if_user_has_not_used_fold_column():
    print("Check fold_column is being specified when we are attempting to use kfold strategy")
    targetColumnName = "survived"
    foldColumnName = "kfold_column"

    teColumns = ["home.dest", "cabin", "embarked"]
    # Here we are not specifying `fold_column`
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName, blended_avg= True, inflection_point = 3, smoothing = 1)
    
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    targetEncoder.fit(trainingFrame)

    try:
        # We expect to get error as we are trying to use kfold strategy but encoding map was created without folds
        targetEncoder.transform(frame=trainingFrame, holdout_type="kfold", seed=1234)
        assert False
    except ValueError:
        print('Good, exception was thrown as expected')


def test_that_warning_will_be_shown_if_we_add_noise_for_none_strategy():
    print("Check that warning will be shown if user is trying to apply noise for holdout_type = `none` case")
    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName, blended_avg= True, inflection_point = 3, smoothing = 1)
    
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()

    targetEncoder.fit(trainingFrame)

    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter("always")
        targetEncoder.transform(frame=trainingFrame, holdout_type="none", noise=0.1, seed=1234)
        assert len(w) == 1
        assert issubclass(w[-1].category, UserWarning)
        assert "Attempt to apply noise with holdout_type=`none` strategy" == str(w[-1].message)


def test_that_encoding_maps_are_accessible_as_frames():
    print("Check that we can access encoding maps as data frames")
    targetColumnName = "survived"
    foldColumnName = "kfold_column" # it is strange that we can't set name for generated kfold

    teColumns = "home.dest"
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  fold_column= foldColumnName, blending_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    targetEncoder.fit(frame=trainingFrame)

    encodingMapFramesKeys = targetEncoder.encoding_map_frames()

    assert len([value for value in encodingMapFramesKeys[0].columns if value in teColumns]) > 0


testList = [
    test_target_encoding_parameters,
    test_target_encoding_fit_method,
    test_target_encoding_transform_kfold,
    test_target_encoding_transform_loo,
    test_target_encoding_transform_none,
    test_target_encoding_transform_none_blending,
    test_target_encoding_default_noise_is_applied,
    test_target_encoding_seed_is_working,
    test_ability_to_pass_column_parameters_as_indexes,
    test_that_both_deprecated_and_new_parameters_are_working_together,
    test_teColumns_parameter_as_single_column_name,
    test_teColumns_parameter_as_single_column_index,
    pubdev_6474_test_more_than_two_columns_to_encode_case,
    test_blending_params_are_within_valid_range,
    test_that_error_will_be_thrown_if_user_has_not_used_fold_column,
    test_that_warning_will_be_shown_if_we_add_noise_for_none_strategy,
    test_that_encoding_maps_are_accessible_as_frames
]

if __name__ == "__main__":
    for test in testList: pyunit_utils.standalone_test(test)
else:
    for test in testList: test()
