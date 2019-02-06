import h2o
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.model import mojo_delegating
from tests import pyunit_utils


def mojo_model_test():

    # GBM
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    gbm = H2OGradientBoostingEstimator(ntrees = 1)
    gbm.train(x = ["Origin", "Dest"], y = "IsDepDelayed", training_frame=airlines)

    filename = tempfile.mkdtemp()
    filename = gbm.download_mojo(filename)
    
    response = mojo_delegating.MojoDelegatingModel(filename)
    print(response.response)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_test)
else:
    mojo_model_test()
