import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.psvm import H2OSupportVectorMachineEstimator


def svm_svmguide1():
    svmguide1 = h2o.import_file(pyunit_utils.locate("smalldata/svm_test/svmguide1.svm"))
    svmguide1_test = h2o.import_file(pyunit_utils.locate("smalldata/svm_test/svmguide1_test.svm"))

    # response is not -1/1 - needs to be explicitly converted
    svmguide1["C1"] = svmguide1["C1"].asfactor()
    svmguide1_test["C1"] = svmguide1_test["C1"].asfactor()

    svm = H2OSupportVectorMachineEstimator(gamma=0.01, rank_ratio=0.1, disable_training_metrics=False)
    svm.train(y="C1", training_frame=svmguide1, validation_frame=svmguide1_test)
    svm.show()

    pred = svm.predict(test_data=svmguide1)
    assert len(pred) == len(svmguide1)

    accuracy = svm.model_performance(valid=True).accuracy()[0][1]
    assert accuracy >= 0.95


if __name__ == "__main__":
    pyunit_utils.standalone_test(svm_svmguide1)
else:
    svm_svmguide1()
