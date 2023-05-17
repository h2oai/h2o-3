import sys

sys.path.insert(1, "../../")
import h2o
import os
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def download_pojo():
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    
    
    # Compensate slash at the end
    model = H2OGradientBoostingEstimator(ntrees=1)
    model.train(x=list(range(4)), y=4, training_frame=iris)

    export_dir = pyunit_utils.locate("results") + "/downloadable_pojo"
    h2o.download_pojo(model=model, path=export_dir)
    assert os.path.isdir(export_dir)
    assert os.path.exists(os.path.join(export_dir, model.model_id + '.java'))
    
    # Slash present at the end
    model = H2OGradientBoostingEstimator(ntrees=1)
    model.train(x=list(range(4)), y=4, training_frame=iris)
    export_dir = pyunit_utils.locate("results") + "/downloadable_pojo/"
    h2o.download_pojo(model=model, path=export_dir)
    assert os.path.isdir(export_dir)
    assert os.path.exists(os.path.join(export_dir, model.model_id + '.java'))

if __name__ == "__main__":
    pyunit_utils.standalone_test(download_pojo)
else:
    download_pojo()
