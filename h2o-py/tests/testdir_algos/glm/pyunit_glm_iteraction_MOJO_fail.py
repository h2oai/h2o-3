from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.exceptions import H2OValueError
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import os

def interactions_airlines():
    airlines = h2o.import_file(pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))
    interaction_pairs = [
        ("CRSDepTime", "UniqueCarrier"),
        ("CRSDepTime", "Origin"),
        ("UniqueCarrier", "Origin")
    ]
    y = 'IsDepDelayed'
    model = H2OGeneralizedLinearEstimator(
        family="Binomial",
        interaction_pairs=interaction_pairs,
    )
    model.train(y=y, training_frame=airlines)
    MOJONAME = pyunit_utils.getMojoName(model._id)
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))
    os.mkdir(TMPDIR)
    try:
        model.download_mojo(path=TMPDIR)
        assert False, "Download MOJO should fail."
    except H2OValueError as e:
        assert "Export to MOJO not supported" in e.args[0]
    try:
        model.download_pojo(path=TMPDIR)
        assert False, "Download POJO should fail."
    except H2OValueError as e:
        assert 'Export to POJO not supported' in e.args[0]
    # should work without interaction pairs
    model = H2OGeneralizedLinearEstimator(family="Binomial")
    model.train(y=y, training_frame=airlines)
    model.download_mojo(path=TMPDIR)
    model.download_pojo(path=TMPDIR)


if __name__ == "__main__":
    pyunit_utils.standalone_test(interactions_airlines)
else:
    interactions_airlines()
