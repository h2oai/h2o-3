import os
import sys
import tempfile

sys.path.insert(1, "../../")

import h2o
from h2o.display import H2OTableDisplay, capture_output, user_tips_enabled
from h2o.estimators import H2ODeepLearningEstimator, H2OGenericEstimator
from tests import pyunit_utils
from tests.testdir_generic_model import compare_output, compare_params
import re


def deeplearning_compare_output(original, generic, strip_part, algo_name, generic_algo_name):
    # Strip algo identifiers and Model keys before output comparison
    original = re.sub(r"H2ODeepLearningEstimator :[a-zA-Z ]*", "", original)
    original = re.sub(r"Model Key:[a-zA-Z0-9 _]*", "", original)
    generic = re.sub(r"H2OGenericEstimator :[a-zA-Z ]*", "", generic)
    generic = re.sub(r"Model Key:[a-zA-Z0-9 _]*", "", generic)

    compare_output(original, generic, strip_part, algo_name, generic_algo_name)


def test(x, y, output_test, strip_part, algo_name, generic_algo_name):
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    gbm = H2ODeepLearningEstimator(epochs=1)
    gbm.train(x=x, y=y, training_frame=airlines, validation_frame=airlines)
    
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (original_output, _):
        gbm.show()
    print(original_output.getvalue())

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = gbm.download_mojo(original_model_filename)

    key = h2o.lazy_import(original_model_filename)
    fr = h2o.get_frame(key[0])
    generic_mojo_model = H2OGenericEstimator(model_key=fr)
    generic_mojo_model.train()
    compare_params(gbm, generic_mojo_model)
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (generic_output, _):
        generic_mojo_model.show()
    print(generic_output.getvalue())

    output_test(original_output.getvalue(), generic_output.getvalue(), strip_part, algo_name, generic_algo_name)

    predictions = generic_mojo_model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert generic_mojo_model._model_json["output"]["model_summary"] is not None
    assert len(generic_mojo_model._model_json["output"]["model_summary"]._cell_values) > 0

    # Test constructor generating the model from existing MOJO file
    generic_mojo_model_from_file = H2OGenericEstimator.from_file(original_model_filename)
    assert generic_mojo_model_from_file is not None
    predictions = generic_mojo_model_from_file.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert generic_mojo_model_from_file._model_json["output"]["model_summary"] is not None
    assert len(generic_mojo_model_from_file._model_json["output"]["model_summary"]._cell_values) > 0

    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo")
    generic_mojo_filename = generic_mojo_model_from_file.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(original_model_filename)


def deeplearning_mojo_model_test():
    test(["Origin", "Dest", "IsDepDelayed"], "Distance", deeplearning_compare_output, "", 'ModelMetricsRegression: deeplearning',
         'ModelMetricsRegressionGeneric: generic')


if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_mojo_model_test)
else:
    deeplearning_mojo_model_test()
