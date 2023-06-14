import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.psvm import H2OSupportVectorMachineEstimator


def svm_smoke():
    splice = h2o.import_file(pyunit_utils.locate("smalldata/splice/splice.svm"))
    svm = H2OSupportVectorMachineEstimator(gamma=0.01, rank_ratio=0.1, disable_training_metrics=False)
    svm.train(y="C1", training_frame=splice)

    pred = svm.predict(test_data=splice)
    assert len(pred) == len(splice)

    accuracy = svm.model_performance(train=True).accuracy()[0][1]
    print("Accuracy (on train): %s" % accuracy)


if __name__ == "__main__":
    pyunit_utils.standalone_test(svm_smoke)
else:
    svm_smoke()
