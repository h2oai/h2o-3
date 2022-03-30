import tempfile
import os
import sys
sys.path.insert(1,"../../")

import h2o
from h2o.estimators import H2OExtendedIsolationForestEstimator, H2OGenericEstimator
from tests import pyunit_utils, compare_frames
from tests.testdir_generic_model import compare_output, Capturing, compare_params


def mojo_model_eif_test():

    train = h2o.import_file(path=pyunit_utils.locate("smalldata/anomaly/single_blob.csv"))
    eif = H2OExtendedIsolationForestEstimator(ntrees=1, extension_level=train.ncol - 1, seed=1234)
    eif.train(training_frame=train)
    prediction_orig = eif.predict(train)
    print(eif)
    print(prediction_orig)
    with Capturing() as original_output:
        eif.show()

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = eif.download_mojo(original_model_filename)

    model = H2OGenericEstimator.from_file(original_model_filename)
    assert model is not None
    print(model)
    compare_params(eif, model)
    with Capturing() as generic_output:
        model.show()

    strip_part = "'Model Summary: '"
    algo_name = 'ModelMetricsAnomaly: extendedisolationforest'
    generic_algo_name = 'ModelMetricsAnomaly: generic'

    compare_output(str(original_output), str(generic_output), strip_part, algo_name, generic_algo_name)
    predictions = model.predict(train)
    print(predictions)
    assert predictions is not None
    assert predictions.nrows == 500
    assert model._model_json["output"]["variable_importances"] is None
    assert model._model_json["output"]["model_summary"] is not None
    assert len(model._model_json["output"]["model_summary"]._cell_values) > 0
    assert compare_frames(prediction_orig, predictions, numElements=-1, strict=True)

    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo");
    generic_mojo_filename = model.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(original_model_filename)


if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_eif_test)
else:
    mojo_model_eif_test()
