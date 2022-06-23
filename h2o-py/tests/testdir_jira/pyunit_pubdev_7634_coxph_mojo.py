import sys
import tempfile
import shutil

import pandas
import numpy as np
from pandas.testing import assert_frame_equal

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def coxph_mojo_predict_with_interactions(sandbox_dir):
    np.random.seed(1234)
    n_rows = 10
    start = np.random.choice([0, 1, 2, 3, 4], size=10)
    delta = np.random.choice([1, 2, 3], size=10)
    data = {
        "start": start,
        "stop": start + delta,
        "X1": np.random.randn(n_rows),
        "X2": np.random.randn(n_rows),
        "age": np.random.choice(["young", "old"], 10),
        "W": np.random.choice([10, 20], size=n_rows),
        "Offset": np.random.uniform(0, 1, 10),
        "Y": np.random.choice([0, 1], size=n_rows)
    }
    train = h2o.H2OFrame(pandas.DataFrame(data))
    train["age"] = train["age"].asfactor()
    h2o_model = H2OCoxProportionalHazardsEstimator(
        start_column="start",
        stop_column="stop",
        weights_column="W",
        offset_column="Offset",
        interactions=["X1", "X2"],
        stratify_by=["age"]
    )

    h2o_model.train(x=["X1", "X2", "age"], y="Y", training_frame=train)
    mojo = pyunit_utils.download_mojo(h2o_model)

    # export new file (including the random columns)
    input_csv = "%s/in.csv" % sandbox_dir
    h2o.export_file(train, input_csv)
    pandas_frame = pandas.read_csv(input_csv)

    h2o_prediction = h2o_model.predict(train)
    mojo_prediction = h2o.mojo_predict_pandas(dataframe=pandas_frame, **mojo)

    assert len(mojo_prediction) == h2o_prediction.nrow
    assert_frame_equal(h2o_prediction.as_data_frame(use_pandas=True), mojo_prediction, check_dtype=False)


pandas_test_dir = tempfile.mkdtemp()
try:
    if __name__ == "__main__":
        pyunit_utils.standalone_test(lambda: coxph_mojo_predict_with_interactions(pandas_test_dir))
    else:
        coxph_mojo_predict_with_interactions(pandas_test_dir)
finally:
    shutil.rmtree(pandas_test_dir)
