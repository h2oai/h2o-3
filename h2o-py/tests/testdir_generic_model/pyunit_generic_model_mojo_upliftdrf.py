import tempfile
import os
import sys
sys.path.insert(1,"../../")

import h2o
from h2o.display import H2OTableDisplay, capture_output
from h2o.estimators import H2OUpliftRandomForestEstimator, H2OGenericEstimator
from tests import pyunit_utils
from tests.testdir_generic_model import compare_output, compare_params


def test(x, y, treatment_column, output_test, strip_part, algo_name, generic_algo_name):

    seed = 12345

    train_h2o = h2o.upload_file(pyunit_utils.locate("smalldata/uplift/upliftml_train.csv"))
    train_h2o[treatment_column] = train_h2o[treatment_column].asfactor()
    train_h2o[y] = train_h2o[y].asfactor()

    n_samples = train_h2o.shape[0]

    uplift_model = H2OUpliftRandomForestEstimator(
        ntrees=5,
        max_depth=5,
        treatment_column=treatment_column,
        uplift_metric="KL",
        distribution="bernoulli",
        min_rows=10,
        nbins=1000,
        seed=seed,
        sample_rate=0.99,
        auuc_type="gain"
    )
    
    uplift_model.train(y=y, x=x, training_frame=train_h2o)
    print(uplift_model)

    # comparison fails when using pandas due to spaces formatting
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (original_output, _): 
        uplift_model.show()
    print(original_output.getvalue())

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = uplift_model.download_mojo(original_model_filename)

    model = H2OGenericEstimator.from_file(original_model_filename)
    assert model is not None
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (generic_output, _):
        model.show()
    print(generic_output.getvalue())
    compare_params(uplift_model, model)

    output_test(original_output.getvalue(), generic_output.getvalue(), strip_part, algo_name, generic_algo_name)
    predictions = model.predict(train_h2o)
    assert predictions is not None
    assert predictions.nrows == n_samples
    assert model._model_json["output"]["model_summary"] is not None
    assert len(model._model_json["output"]["model_summary"]._cell_values) > 0

    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo");
    generic_mojo_filename = model.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(original_model_filename)


def mojo_model_test_binomial_uplift():
    test(["feature_"+str(x) for x in range(1, 13)], "outcome", "treatment",  compare_output, "Model Summary: ", 
         'ModelMetricsBinomialUplift: upliftdrf', 'ModelMetricsBinomialUpliftGeneric: generic')


pyunit_utils.run_tests([
    mojo_model_test_binomial_uplift,
])
