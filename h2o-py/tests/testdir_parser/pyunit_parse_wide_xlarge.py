import os
import sys
import time
sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils as pu


def test_parse_wide_dataset():
    n_cols = 1_000_000
    results_dir = pu.locate("results")
    wide_path = os.path.join(results_dir, "wide1M.svmlight")

    with open(wide_path, "w") as text_file:
        text_file.write("1 %s:2.72" % n_cols)

    data_start = time.time()
    parsed = h2o.import_file(wide_path)
    data_duration = time.time() - data_start
    names = parsed.names
    print("wide data parsing took {}s".format(data_duration))
    assert len(names) == n_cols + 1


pu.run_tests([
    test_parse_wide_dataset
])

