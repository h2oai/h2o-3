import h2o
from h2o.exceptions import H2OResponseError
from tests import pyunit_utils
import tempfile
from collections import OrderedDict
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def test_frame_reload():
    work_dir = tempfile.mkdtemp()
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    df_key = iris.key
    df_pd_orig = iris.as_data_frame()
    iris.save(work_dir)
    try:
        iris.save(work_dir, force=False)  # fails because file exists
    except H2OResponseError as e:
        assert e.args[0].exception_msg.startswith("File already exists")
    try:
        h2o.load_frame(df_key, work_dir, force=False)  # fails because frame exists
    except H2OResponseError as e:
        assert e.args[0].exception_msg == "Frame Key<Frame> iris_wheader.hex already exists."
    df_loaded_force = h2o.load_frame(df_key, work_dir) 
    h2o.remove(iris)
    df_loaded = h2o.load_frame(df_key, work_dir, force=False)
    df_pd_loaded_force = df_loaded_force.as_data_frame()
    df_pd_loaded = df_loaded.as_data_frame()
    assert df_pd_orig.equals(df_pd_loaded_force)
    assert df_pd_orig.equals(df_pd_loaded)
    
    # try running grid search on the frame
    h2o.remove_all()
    df_loaded = h2o.load_frame(df_key, work_dir)
    hyper_parameters = OrderedDict()
    hyper_parameters["ntrees"] = [5, 10, 20, 30]
    grid_small = H2OGridSearch(
        H2OGradientBoostingEstimator,
        hyper_params=hyper_parameters
    )
    grid_small.train(x=list(range(4)), y=4, training_frame=df_loaded)
    assert len(grid_small.models) == 4


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_frame_reload)
else:
    test_frame_reload()
