from __future__ import print_function
import sys, os
import tempfile

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OTargetEncoderEstimator
from h2o.estimators import H2OGradientBoostingEstimator

"""
This test is used to check Rapids wrapper for java TargetEncoder
"""

def test_target_encoding_fit_method():
    print("Check fit method of the TargetEncoder class")
    targetColumnName = "survived"
    foldColumnName = "kfold_column"

    teColumns = ["home.dest", "cabin", "embarked"]
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)
    
    te = H2OTargetEncoderEstimator(k = 0.7, f = 0.3, data_leakage_handling = "None")
    te.train(training_frame=trainingFrame, x=teColumns, y=targetColumnName)
    print(te)
    transformed = te.transform(frame = trainingFrame)
    
    assert transformed is not None
    print(transformed.names)
    assert transformed.ncols == trainingFrame.ncols + len(teColumns)
    for te_col in teColumns:
        assert te_col + "_te" in transformed.names
    
    assert transformed.nrows == 1309
    
    # Test fold_column proper handling + kfold data leakage strategy defined
    te = H2OTargetEncoderEstimator(k=0.7, f=0.3)
    te.train(training_frame=trainingFrame, fold_column="pclass", x=teColumns, y=targetColumnName)
    transformed = te.transform(trainingFrame, data_leakage_handling="kfold", seed = 1234)

    te.train(training_frame=trainingFrame, fold_column="pclass", x=teColumns, y=targetColumnName)
    
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
    transformed = te.transform(trainingFrame, data_leakage_handling="kfold", seed=1234)
    expected_columns = ['home.dest', 'pclass', 'embarked', 'cabin', 'sex', 'survived', 'name', 'age',
           'sibsp', 'parch', 'ticket', 'fare', 'boat', 'body', 'kfold_column',
           'sex_te', 'cabin_te', 'embarked_te', 'home.dest_te']
    assert len(transformed.col_names) == len(expected_columns)
    assert sorted(transformed.col_names) == sorted(expected_columns) # 4 encoded columns

    gbm_with_te=H2OGradientBoostingEstimator(score_tree_interval=10,
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


def test_target_encoder_model_output_has_te_model_field():
    print("Check that Target Encoder model's output has `te_model` field")
    targetColumnName = "survived"
    foldColumnName = "kfold_column"

    teColumns = ["cabin", "embarked"]
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    te = H2OTargetEncoderEstimator(k = 0.7, f = 0.3, data_leakage_handling = "KFold", fold_column=foldColumnName)
    te.train(training_frame=trainingFrame, x=teColumns, y=targetColumnName)

    assert te._model_json['output']['te_model'] is None


testList = [
    test_target_encoding_fit_method,
    test_target_encoder_model_output_has_te_model_field
]

pyunit_utils.run_tests(testList)
