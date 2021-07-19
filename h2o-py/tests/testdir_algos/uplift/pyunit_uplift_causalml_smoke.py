from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
from tests import pyunit_utils
from causalml.inference.tree import UpliftRandomForestClassifier
from causalml.dataset import make_uplift_classification
import causalml


def causalml_smoke():
    print("Causalml library smoke test")
    print("Version of causalml: {0}".format(causalml.__version__))
    n_samples = 500
    seed = 12345

    train, x_names = make_uplift_classification(n_samples=n_samples,
                                                treatment_name=['control', 'treatment'],
                                                n_classification_features=10,
                                                n_classification_informative=10,
                                                random_seed=seed
                                                )
    assert not train.empty
    assert x_names

    treatment_column = "treatment_group_key"
    response_column = "conversion"
    train[treatment_column] = train[treatment_column].astype(str)
    uplift_model = UpliftRandomForestClassifier(
        n_estimators=5,
        max_depth=8,
        evaluationFunction="KL",
        control_name="control",
        min_samples_leaf=10,
        min_samples_treatment=10,
        normalization=False,
        random_state=42,
    )
    uplift_model.fit(
        train[x_names].values,
        treatment=train[treatment_column].values,
        y=train[response_column].values
    )

    assert uplift_model


if __name__ == "__main__":
    pyunit_utils.standalone_test(causalml_smoke)
else:
    causalml_smoke()
