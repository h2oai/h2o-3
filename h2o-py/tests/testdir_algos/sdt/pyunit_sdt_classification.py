from h2o import H2OFrame
from tests import pyunit_utils
import pandas as pd
from sklearn.model_selection import train_test_split
import h2o
from h2o.estimators import H2OSingleDecisionTreeEstimator
from sklearn.metrics import accuracy_score, f1_score


max_depth = 5
DATA_PATH = "/Users/yuliia/work/h2o-3/benchmark_data/"


def test_sdt_binary_classification():
    data = pd.read_csv(DATA_PATH + "Dataset1_train.csv")
    target_variable = 'label'
    train, test = train_test_split(data, train_size=0.7)
    train_frame: H2OFrame = h2o.H2OFrame(train)
    test_frame: H2OFrame = h2o.H2OFrame(test.drop([target_variable], axis=1))
    y_train = train[[target_variable]]
    y_test = test[[target_variable]]

    sdt_h2o: H2OSingleDecisionTreeEstimator = H2OSingleDecisionTreeEstimator(model_id="single_decision_tree.hex",
                                                                             max_depth=max_depth)
    sdt_h2o.train(training_frame=train_frame, y=target_variable)

    pred_train = sdt_h2o.predict(train_frame.drop(target_variable)).as_data_frame()
    pred_test = sdt_h2o.predict(test_frame).as_data_frame()

    train_f1 = f1_score(y_train, pred_train, average="macro")
    test_f1 = f1_score(y_test, pred_test, average="macro")

    train_accuracy = accuracy_score(y_train, pred_train)
    test_accuracy = accuracy_score(y_test, pred_test)

    print(train_f1, test_f1, train_accuracy, test_accuracy)

    assert 1 - train_accuracy < 9e-5
    assert 1 - test_accuracy < 9e-5
    assert 1 - train_f1 < 9e-5
    assert 1 - train_f1 < 9e-5


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_sdt_binary_classification)
else:
    test_sdt_binary_classification()
