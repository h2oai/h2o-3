from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
from tests import pyunit_utils
from causalml.dataset import make_uplift_classification
import causalml


def causalml_make_uplift_classification_smoke():
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


if __name__ == "__main__":
    pyunit_utils.standalone_test(causalml_make_uplift_classification_smoke)
else:
    causalml_make_uplift_classification_smoke()
