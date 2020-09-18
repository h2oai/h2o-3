from __future__ import print_function
import sys, os
import tempfile

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.estimators import H2OTargetEncoderEstimator
from h2o.estimators import H2OGradientBoostingEstimator


def test_target_encoding_full_scenario():
    print("Check basic train/predict scenario")
    targetColumnName = "survived"
    foldColumnName = "kfold_column"

    teColumns = ["home.dest", "cabin", "embarked"]
    trainingFrame = h2o.import_file(pu.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)
    
    te = H2OTargetEncoderEstimator(inflection_point=0.7, 
                                   smoothing=0.3, 
                                   data_leakage_handling="None",
                                   seed=1234)
    te.train(training_frame=trainingFrame, 
             x=teColumns, 
             y=targetColumnName)
    transformed = te.transform(trainingFrame, as_training=True)
    
    assert transformed is not None
    assert transformed.ncols == trainingFrame.ncols + len(teColumns)
    for te_col in teColumns:
        assert te_col + "_te" in transformed.names
    
    assert transformed.nrows == 1309
    
    # Test fold_column proper handling + kfold data leakage strategy defined
    te = H2OTargetEncoderEstimator(inflection_point=0.7, 
                                   smoothing=0.3, 
                                   data_leakage_handling="kfold", 
                                   seed=1234)
    te.train(training_frame=trainingFrame,
             x=teColumns,
             y=targetColumnName,
             fold_column="pclass")
    transformed = te.transform(trainingFrame, as_training=True)

    assert transformed is not None
    assert transformed.nrows == 1309
    
    
    # Test MOJO download
    mojo_file = te.download_mojo(tempfile.mkdtemp())
    assert os.path.isfile(mojo_file)
    assert os.path.getsize(mojo_file) > 0

    # Argument check
    te.train(training_frame=trainingFrame, fold_column="pclass", y=targetColumnName, x=teColumns)

    # Drop all non-categorical columns
    te.train(x=None, y=targetColumnName, training_frame=trainingFrame, fold_column="pclass")
    transformed = te.transform(trainingFrame, as_training=True)
    expected_columns = ['home.dest', 'pclass', 'embarked', 'cabin', 'sex', 'survived', 'name', 'age',
           'sibsp', 'parch', 'ticket', 'fare', 'boat', 'body', 'kfold_column',
           'sex_te', 'cabin_te', 'embarked_te', 'home.dest_te']
    assert len(transformed.col_names) == len(expected_columns)
    assert sorted(transformed.col_names) == sorted(expected_columns)  # 4 encoded columns

    gbm_with_te = H2OGradientBoostingEstimator(score_tree_interval=10,
                                               ntrees=500,
                                               sample_rate=0.8,
                                               col_sample_rate=0.8,
                                               seed=1234,
                                               stopping_rounds=5,
                                               stopping_metric="AUC",
                                               stopping_tolerance=0.001,
                                               model_id="gbm_with_te")

    myX = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin_te", "embarked_te", "home.dest_te"]
    gbm_with_te.train(x=myX, y=targetColumnName, training_frame=transformed)


def test_target_encoded_frame_does_not_contain_fold_column():
    print("Check that attached TargetEncoderModel is being used during training and scoring")
    targetColumnName = "survived"
    foldColumnName = "kfold_column"

    teColumns = ["cabin", "embarked"]
    trainingFrame = h2o.import_file(pu.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    te = H2OTargetEncoderEstimator(inflection_point=0.7, 
                                   smoothing=0.3, 
                                   data_leakage_handling="KFold", 
                                   fold_column=foldColumnName,
                                   seed=1234)
    te.train(training_frame=trainingFrame, x=teColumns, y=targetColumnName)
    model_summary = te._model_json['output']['model_summary'].as_data_frame()
    encoded_column_names = model_summary['encoded_column_name']

    # Checking that we don't have empty entries in TwoDim table
    assert len(model_summary) == 2

    encoded_columns_with_te_suffix = model_summary[encoded_column_names.str.contains('_te')]
    assert len(encoded_columns_with_te_suffix) == 2

    transformed = te.transform(trainingFrame, as_training=True)

    # Checking that fold column is not being encoded.
    assert foldColumnName+"_te" not in transformed.col_names


def test_original_features_are_kept_by_default():
    target = "survived"
    teColumns = ["cabin", "embarked"]
    trainingFrame = h2o.import_file(pu.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[target] = trainingFrame[target].asfactor()

    te = H2OTargetEncoderEstimator()
    te.train(training_frame=trainingFrame, x=teColumns, y=target)
    
    transformed = te.transform(trainingFrame)
    for col in teColumns:
        assert "{}_te".format(col) in transformed.names
        assert col in transformed.names
    

def test_original_features_can_be_automatically_removed_from_result_frame():
    target = "survived"
    teColumns = ["cabin", "embarked"]
    trainingFrame = h2o.import_file(pu.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[target] = trainingFrame[target].asfactor()

    te = H2OTargetEncoderEstimator(keep_original_categorical_columns=False)
    te.train(training_frame=trainingFrame, x=teColumns, y=target)

    transformed = te.transform(trainingFrame)
    for col in teColumns:
        assert "{}_te".format(col) in transformed.names
        assert col not in transformed.names


pu.run_tests([
    test_target_encoding_full_scenario,
    test_target_encoded_frame_does_not_contain_fold_column,
    test_original_features_are_kept_by_default,
    test_original_features_can_be_automatically_removed_from_result_frame
])
