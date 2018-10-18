from __future__ import print_function
import sys, os, time
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.targetencoder import TargetEncoder
import json

"""
This test is used to check Rapids wrapper for java TargetEncoder
"""

# def import_dataset():
#     df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
#     target = "CAPSULE"
#     df[target] = df[target].asfactor()
#     #Split frames
#     fr = df.split_frame(ratios=[.8,.1])
#     #Set up train, validation, and test sets
#     return dict(train=fr[0], valid=fr[1], test=fr[2], target=target, target_idx=1)


def test_target_encoding_parameters():
    print("Check arguments to TargetEncoder class")
    targetEncoder = TargetEncoder(teColumns=["teColumn1"])

    assert targetEncoder._teColumns == ["teColumn1"]

def test_target_encoding_fit_method():
    print("Check fit method of the TargetEncoder class")
    targetColumnName = "survived"
    foldColumnName = "kfold_column" # it is strange that we can't set name for generated kfold

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(teColumns= teColumns, targetColumnName = targetColumnName,
                                  foldColumnName = foldColumnName, blending = True)
    trainingFrame = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic_train.csv"), header=1)

    trainingFrame[targetColumnName] = trainingFrame[targetColumnName].asfactor()
    trainingFrame[foldColumnName] = trainingFrame.kfold_column(n_folds=5, seed=1234)

    encodingMap = targetEncoder.fit(trainingFrame=trainingFrame)
    # for extra inspiration go to IcedHashMapStringModelBuilderSchema
    assert encodingMap.teColumns['string'] == teColumns
    assert encodingMap.frames[0]['num_rows'] == 484


tests = [
    test_target_encoding_parameters,
    test_target_encoding_fit_method,
]

if __name__ == "__main__":
    for test in tests: pyunit_utils.standalone_test(test)
else:
    for test in tests: test()
