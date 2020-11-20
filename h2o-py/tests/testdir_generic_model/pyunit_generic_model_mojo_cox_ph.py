import tempfile
import os
import sys
sys.path.insert(1,"../../")

import h2o
from h2o.estimators import H2OCoxProportionalHazardsEstimator, H2OGenericEstimator
from tests import pyunit_utils
from tests.testdir_generic_model import compare_output, Capturing, compare_params


def test(output_test, x, ties):

    heart = h2o.import_file(path=pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    
    coxph = H2OCoxProportionalHazardsEstimator(
        start_column="start",
        stop_column="stop"
    )
    coxph.train(x=x, y="event", training_frame=heart)
    with Capturing() as original_output:
        coxph.show()

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = coxph.download_mojo(original_model_filename)
      
    model = H2OGenericEstimator.from_file(original_model_filename)
    assert model is not None
    compare_params(coxph, model)
    predictions = model.predict(heart)
    predictions_orig = coxph.predict(heart)
    assert predictions is not None
    assert predictions.nrows == heart.nrows
    assert predictions == predictions_orig

    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo")
    generic_mojo_filename = model.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(original_model_filename)

def mojo_model_test_coxph():
    for x in [["age", "transplant"], ["age", "surgery", "transplant"], ["age", "surgery", "transplant", "year"]]:
        for ties in ["efron", "breslow"]:
            test(compare_output, x, ties)

if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_test_coxph)
else:
    mojo_model_test_coxph()
