import sys

from sklearn.metrics import accuracy_score

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils
from h2o.estimators import H2ODecisionTreeEstimator


def test_dt_multinomial():
    data = h2o.import_file(pyunit_utils.locate("smalldata/sdt/sdt_3EnumCols_10kRows_multinomial.csv"))
    response_col = "response"
    data[response_col] = data[response_col].asfactor()

    predictors = ["C1", "C2", "C3"]

    # train model
    dt = H2ODecisionTreeEstimator(max_depth=3)
    dt.train(x=predictors, y=response_col, training_frame=data)

    dt.show()

    pred_train = dt.predict(data).as_data_frame(use_pandas=True)['predict']

    train_accuracy = accuracy_score(data[response_col].as_data_frame(use_pandas=True), pred_train)

    print(train_accuracy)

    assert train_accuracy >= 0.8555


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_dt_multinomial)
else:
    test_dt_multinomial()
