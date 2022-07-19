import os
import sys
import tempfile

sys.path.insert(1, "../../")

import h2o
from h2o.display import H2OTableDisplay, capture_output
from h2o.estimators import H2OGenericEstimator, H2OGradientBoostingEstimator, \
    H2ORandomForestEstimator, H2OStackedEnsembleEstimator
from tests import pyunit_utils
from tests.testdir_generic_model import compare_output, compare_params


def stackedensemble_mojo_model_test():
    train = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_test.csv"))
    x = train.columns
    y = "species"

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True)
    gbm.train(x=x, y=y, training_frame=train)
    rf = H2ORandomForestEstimator(nfolds=nfolds,
                                  fold_assignment="Modulo",
                                  keep_cross_validation_predictions=True)
    rf.train(x=x, y=y, training_frame=train)
    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     validation_frame=test,
                                     base_models=[gbm.model_id, rf.model_id])
    se.train(x=x, y=y, training_frame=train)
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (original_output, _):
        se.show()
    print(original_output.getvalue())

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = se.download_mojo(original_model_filename)

    key = h2o.lazy_import(original_model_filename)
    fr = h2o.get_frame(key[0])
    generic_mojo_model = H2OGenericEstimator(model_key=fr)
    generic_mojo_model.train()
    compare_params(se, generic_mojo_model)

    predictions = generic_mojo_model.predict(test)
    assert predictions is not None

    # Test constructor generating the model from existing MOJO file
    generic_mojo_model_from_file = H2OGenericEstimator.from_file(original_model_filename)
    assert generic_mojo_model_from_file is not None
    predictions = generic_mojo_model_from_file.predict(test)
    assert predictions is not None

    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo")
    generic_mojo_filename = generic_mojo_model_from_file.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(original_model_filename)


if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_mojo_model_test)

else:
    stackedensemble_mojo_model_test()
