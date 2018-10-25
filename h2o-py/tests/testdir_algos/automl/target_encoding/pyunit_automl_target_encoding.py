from __future__ import print_function
import sys, os, time
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
                                  fold_column= foldColumnName, blending_avg= True, inflection_point = 3, smoothing = 1)
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
                                  fold_column= foldColumnName, blending_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    targetEncoder.fit(trainingFrame)

    encodedFrame = targetEncoder.transform(frame=trainingFrame, holdout_type="kfold", seed=1234, is_train_or_valid=True)

    teColumnsEncoded = list(map(lambda x: x+"_te", teColumns))
    frameWithEncodingsOnly = encodedFrame[teColumnsEncoded]
    assert frameWithEncodingsOnly.ncols == 3


def test_target_encoding_transform_loo():
    print("Check transform (loo strategy) of the TargetEncoder class")
    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  fold_column='', blending_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()

    targetEncoder.fit(frame=trainingFrame)

    encodedFrame = targetEncoder.transform(frame=trainingFrame, holdout_type="loo", seed=1234, is_train_or_valid=True)

    teColumnsEncoded = list(map(lambda x: x+"_te", teColumns))
    frameWithEncodingsOnly = encodedFrame[teColumnsEncoded]
    assert frameWithEncodingsOnly.ncols == 3


def test_target_encoding_transform_none():
    print("Check transform (none strategy) of the TargetEncoder class")
    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  blending_avg= True, inflection_point = 3, smoothing = 1)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()

    targetEncoder.fit(frame=trainingFrame)

    encodedFrame = targetEncoder.transform(frame=trainingFrame, holdout_type="none", seed=1234, is_train_or_valid=True)

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
                                              blending_avg= True, inflection_point = 3, smoothing = 1)
    
    targetEncoderWithBlending.fit(frame=trainingFrame)

    encodedFrameWithBlending = targetEncoderWithBlending.transform(frame=trainingFrame, holdout_type="none", seed=1234, is_train_or_valid=True)

    frameWithBlendedEncodingsOnly = encodedFrameWithBlending[teColumnsEncoded]

    targetEncoderWithoutBlending = TargetEncoder(x= teColumns, y= targetColumnName,
                                                 blending_avg= False, inflection_point = 3, smoothing = 1)

    targetEncoderWithoutBlending.fit(frame=trainingFrame)

    encodedFrameWithoutBlending = targetEncoderWithoutBlending.transform(frame=trainingFrame, holdout_type="none", seed=1234, is_train_or_valid=True)
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
                                  blending_avg= True, inflection_point = 3, smoothing = 1)

    targetEncoder.fit(frame=trainingFrame)

    encodedFrame = targetEncoder.transform(frame=trainingFrame, holdout_type="none", noise=noiseTest, seed=1234, is_train_or_valid=True)

    encodingsOnly = encodedFrame[teColumnsEncoded]

    # Second transformation with the same seed 1234
    encodedFrame2 = targetEncoder.transform(frame=trainingFrame, holdout_type="none", noise=noiseTest, seed=1234, is_train_or_valid=True)
    encodingsOnly2 = encodedFrame2[teColumnsEncoded]

    # Third  transformation with another seed 1235
    encodedFrame3 = targetEncoder.transform(frame=trainingFrame, holdout_type="none", noise=noiseTest, seed=1235, is_train_or_valid=True)
    encodingsOnly3 = encodedFrame3[teColumnsEncoded]

    # Comparing results
    # First two encodings should be equal
    assert pyunit_utils.compare_frames(encodingsOnly, encodingsOnly2, 10, tol_time=0, tol_numeric=1e-6)
    # Third encoding should be different from the first two ones
    try:
        pyunit_utils.compare_frames(encodingsOnly, encodingsOnly3, 10, tol_time=0, tol_numeric=1e-6)
        assert False
    except AssertionError:
        print('Good, encodings are different as expected. Seed is working.')


def test_target_encoding_default_noise_is_applied():
    print("Check that seed is applied when we use noise. Noise is set to the same values. Only seed is different.")

    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    teColumnsEncoded = list(map(lambda x: x+"_te", teColumns))
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)
    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()

    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  blending_avg= True, inflection_point = 3, smoothing = 1)

    targetEncoder.fit(frame=trainingFrame)

    seedTest = 1234
    encodedFrame = targetEncoder.transform(frame=trainingFrame, holdout_type="none", noise=0.0, seed=seedTest, is_train_or_valid=True)

    encodingsOnly = encodedFrame[teColumnsEncoded]

    # Second transformation without specifying noise. Default will be applied.
    encodedFrame2 = targetEncoder.transform(frame=trainingFrame, holdout_type="none", seed=seedTest, is_train_or_valid=True)
    encodingsOnly2 = encodedFrame2[teColumnsEncoded]

    # Third  transformation with zero noise
    encodedFrame3 = targetEncoder.transform(frame=trainingFrame, holdout_type="none", noise=0.0, seed=seedTest, is_train_or_valid=True)
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

testList = [
    test_target_encoding_parameters,
    test_target_encoding_fit_method,
    test_target_encoding_transform_kfold,
    test_target_encoding_transform_loo,
    test_target_encoding_transform_none,
    test_target_encoding_transform_none_blending,
    test_target_encoding_default_noise_is_applied,
    test_target_encoding_seed_is_working
]

if __name__ == "__main__":
    for test in testList: pyunit_utils.standalone_test(test)
else:
    for test in testList: test()
