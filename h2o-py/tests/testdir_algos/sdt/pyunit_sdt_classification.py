import h2o
from h2o.estimators import H2OSingleDecisionTreeEstimator
from tests import pyunit_utils
from sklearn.metrics import accuracy_score, f1_score


def test_sdt_binary_classification():
    target_variable = 'CAPSULE'
    train, test = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv")).split_frame(ratios=[.7])
    y_train = train[target_variable].as_data_frame(use_pandas=True)[target_variable]
    y_test = test[target_variable].as_data_frame(use_pandas=True)[target_variable]

    sdt_h2o = H2OSingleDecisionTreeEstimator(model_id="single_decision_tree.hex", max_depth=5)
    sdt_h2o.train(training_frame=train, y=target_variable)

    pred_train = sdt_h2o.predict(train).as_data_frame(use_pandas=True)['predict']
    pred_test = sdt_h2o.predict(test).as_data_frame(use_pandas=True)['predict']

    train_f1 = f1_score(y_train, pred_train, average="macro")
    test_f1 = f1_score(y_test, pred_test, average="macro")

    train_accuracy = accuracy_score(y_train, pred_train)
    test_accuracy = accuracy_score(y_test, pred_test)

    print(train_f1, test_f1, train_accuracy, test_accuracy)

    assert (1 - train_accuracy) < 3e-1
    assert (1 - test_accuracy) < 4e-1
    assert (1 - train_f1) < 3e-1
    assert (1 - train_f1) < 4e-1


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_sdt_binary_classification)
else:
    test_sdt_binary_classification()
