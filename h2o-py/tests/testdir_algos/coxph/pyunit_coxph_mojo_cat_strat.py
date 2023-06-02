import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def test_coxph_mojo_with_stratification_and_categoricals():
    data = h2o.create_frame(categorical_fraction=1, missing_fraction=0, factors=10, has_response=True, seed=10)
    data["C1"] = data["C1"].asnumeric()
    print(data)

    model = H2OCoxProportionalHazardsEstimator(stratify_by=["C2", "C3"], stop_column="C1")
    model.train(y="response", training_frame=data)
    print(model)

    # reference predictions
    h2o_prediction = model.predict(data)

    assert pyunit_utils.test_java_scoring(model, data, h2o_prediction, 1e-8)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_coxph_mojo_with_stratification_and_categoricals)
else:
    test_coxph_mojo_with_stratification_and_categoricals()
