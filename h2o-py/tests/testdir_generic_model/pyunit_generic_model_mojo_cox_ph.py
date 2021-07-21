import tempfile
import os
import sys
sys.path.insert(1,"../../")

import h2o
from h2o.estimators import H2OCoxProportionalHazardsEstimator
from h2o.model.metrics_base import H2OModelMetricsRegressionCoxPH
from tests import pyunit_utils
from tests.testdir_generic_model import compare_params


def test(x, ties, stratify_by, use_all_factor_levels):

    heart = h2o.import_file(path=pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    heart_test = h2o.import_file(path=pyunit_utils.locate("smalldata/coxph_test/heart_test.csv"))
    for colname in stratify_by:
        heart[colname] = heart[colname].asfactor()
        heart_test[colname] = heart_test[colname].asfactor()

    coxph = H2OCoxProportionalHazardsEstimator(
        start_column="start",
        stop_column="stop",
        stratify_by=stratify_by,
        use_all_factor_levels=use_all_factor_levels,
        ties=ties
    )
    coxph.train(x=x, y="event", training_frame=heart)
    coxph.show()

    mojo_path = tempfile.mkdtemp()
    mojo_path = coxph.download_mojo(mojo_path)

    from h2o.estimators import H2OGenericEstimator
    model = H2OGenericEstimator.from_file(mojo_path)
    assert model is not None
    # test printing the model won't cause issues but don't compare - they won't match
    model.show()
    compare_params(coxph, model)

    assert isinstance(model.model_performance(), H2OModelMetricsRegressionCoxPH)
    assert coxph.model_performance().concordance() == model.model_performance().concordance()
    assert coxph.model_performance().concordant() == model.model_performance().concordant()
    assert coxph.model_performance().tied_y() == model.model_performance().tied_y()

    # also check we can get metrics on new data
    assert isinstance(model.model_performance(test_data=heart_test), H2OModelMetricsRegressionCoxPH)
    
    predictions = model.predict(heart_test)
    predictions_orig = coxph.predict(heart_test)
    assert predictions is not None
    assert predictions.nrows == heart_test.nrows
    assert predictions_orig.nrows == heart_test.nrows
    
    pyunit_utils.compare_string_frames_local(predictions, predictions_orig, 0.001)

    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo")
    generic_mojo_filename = model.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(mojo_path)


def mojo_model_test_coxph():
    for x in [["age"], ["age", "transplant"], ["age", "surgery", "transplant"], ["age", "surgery", "transplant", "year"]]:
        for ties in ["efron", "breslow"]:
            for use_all_factor_levels in [True, False, None]:
                test(x, ties, [], use_all_factor_levels) 
    for x in [["age", "transplant"], ["age", "surgery", "transplant"], ["age", "surgery", "transplant", "year"]]:
        for ties in ["efron", "breslow"]:
            for use_all_factor_levels in [True, False, None]:
                test(x, ties, [], use_all_factor_levels)
    for x in [["age", "surgery", "transplant"], ["age", "surgery", "transplant", "year"]]:
        for ties in ["efron", "breslow"]:
            for use_all_factor_levels in [True, False, None]:
                test(x, ties, [], use_all_factor_levels)


if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_test_coxph)
else:
    mojo_model_test_coxph()
