import sys
import tempfile
import shutil
import os

import pandas
import numpy as np
from pandas.testing import assert_frame_equal

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def mojo_predict_pandas_test(sandbox_dir, stratify_by=None):
    if not os.path.exists(sandbox_dir):
        os.makedirs(sandbox_dir)

    # bunch of random columns to be added to the dataset
    random_cols = ["c1", "c2", "c3", "c4"]

    data = h2o.import_file(pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    if stratify_by:
        for strat_col in stratify_by:
            data[strat_col] = data[strat_col].asfactor()
    data['surgery'] = data['surgery'].asfactor()

    data_random_local = pandas.DataFrame(np.random.random(size=(data.nrow, len(random_cols))), columns=random_cols)
    data = data.cbind(h2o.H2OFrame(data_random_local))

    model = H2OCoxProportionalHazardsEstimator(stratify_by=stratify_by, start_column="start", stop_column="stop",
                                               interaction_pairs=[("age", "c1"), ("c1", "c2"), ("c3", "age")])
    model.train(x=["age", "surgery", "transplant"] + random_cols, y="event", 
                training_frame=data)
    print(model)

    # reference predictions
    h2o_prediction = model.predict(data)

    assert pyunit_utils.test_java_scoring(model, data, h2o_prediction, 1e-8)

    # download mojo
    mojo = pyunit_utils.download_mojo(model)

    # export new file (including the random columns)
    input_csv = "%s/in.csv" % sandbox_dir
    h2o.export_file(data, input_csv)
    pandas_frame = pandas.read_csv(input_csv)

    mojo_prediction = h2o.mojo_predict_pandas(dataframe=pandas_frame, **mojo)

    assert len(mojo_prediction) == h2o_prediction.nrow
    assert_frame_equal(h2o_prediction.as_data_frame(use_pandas=True), mojo_prediction, check_dtype=False)


pandas_test_dir = tempfile.mkdtemp()
try:
    if __name__ == "__main__":
        pyunit_utils.standalone_test(lambda: mojo_predict_pandas_test(pandas_test_dir))
        shutil.rmtree(pandas_test_dir)
        pyunit_utils.standalone_test(lambda: mojo_predict_pandas_test(pandas_test_dir, ["transplant"]))
    else:
        mojo_predict_pandas_test(pandas_test_dir)
        shutil.rmtree(pandas_test_dir)
        mojo_predict_pandas_test(pandas_test_dir, ["transplant"])
finally:
    shutil.rmtree(pandas_test_dir)
