from __future__ import print_function
import sys, os
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.utils.typechecks import assert_is_type

def h2oapi():
    """
    Python API test: h2o.load_model(path)
    """
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]

    model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
    model.train(x=X, y=Y, training_frame=training_data)
    try:
        results_dir = pyunit_utils.locate("results")    # find directory path to results folder
        h2o.save_model(model, path=results_dir, force=True)       # save model
        full_path_filename = os.path.join(results_dir, model._id)
        assert os.path.isfile(full_path_filename), "h2o.save_model() command is not working."
        model_reloaded = h2o.load_model(full_path_filename)
        assert_is_type(model, H2OGeneralizedLinearEstimator)
        assert_is_type(model_reloaded, H2OGeneralizedLinearEstimator)

    except Exception as e:
        if 'File not found' in e.args[0]:
            print("Directory is not writable.  h2o.load_model() command is not tested.")
        else:
            assert False, "h2o.load_model() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oapi)
else:
    h2oapi()
