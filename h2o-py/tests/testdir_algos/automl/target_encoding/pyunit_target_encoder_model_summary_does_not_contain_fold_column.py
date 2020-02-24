from __future__ import print_function

import os
import sys

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OTargetEncoderEstimator


def test_target_encoder_model_summary_does_not_contain_fold_column():
    print("Check that Target Encoder's summary does not contain fold_column")
    targetColumnName = "survived"
    foldColumnName = "kfold_column"

    teColumns = ["cabin", "embarked"]
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    te = H2OTargetEncoderEstimator(k = 0.7, f = 0.3, data_leakage_handling = "KFold", fold_column=foldColumnName)
    te.train(training_frame=trainingFrame, x=teColumns, y=targetColumnName)
    print(te)
    print(trainingFrame)

    model_summary = te._model_json['output']['model_summary'].as_data_frame()
    print(model_summary)
    encoded_column_names = model_summary['encoded_column_name']
    # Checking that we don't have empty entries in TwoDim table
    assert len(model_summary) == 2

    encoded_columns_with_te_suffix = model_summary[encoded_column_names.str.contains('_te', regex=True)]
    assert len(encoded_columns_with_te_suffix) == 2

    transformed = te.transform(trainingFrame, data_leakage_handling = "KFold")

    # Checking that fold column is not being encoded.
    assert foldColumnName+"_te" not in transformed.col_names


testList = [
    test_target_encoder_model_summary_does_not_contain_fold_column
]

pyunit_utils.run_tests(testList)
