from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OTargetencoderEstimator

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
    
    te = H2OTargetencoderEstimator(encoded_columns = teColumns, target_column = targetColumnName)
    te.train(x = teColumns, y = targetColumnName, training_frame = trainingFrame)
    transformed = te.predict(trainingFrame)
    
    assert transformed is not None
    print(transformed.names)
    assert transformed.ncols == trainingFrame.ncols + len(teColumns)
    for te_col in teColumns:
        assert te_col + "_te" in transformed.names

testList = [
    test_target_encoding_fit_method
]

if __name__ == "__main__":
    for test in testList: pyunit_utils.standalone_test(test)
else:
    for test in testList: test()
