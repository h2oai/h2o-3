from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from os import path
from timeit import default_timer as timer
import filecmp

'''
Export file using h2o.export_file with parallel export enabled
'''


def export_parallel():
    df = h2o.create_frame(rows=1000000, cols=100, seed=123)

    target_single = path.join(pyunit_utils.locate("results"), "export_file_single.csv")
    target_parallel = path.join(pyunit_utils.locate("results"), "export_file_parallel.csv")

    start = timer()
    h2o.export_file(df, target_single, parallel=False)
    end_single = timer()
    h2o.export_file(df, target_parallel, parallel=True)
    end_parallel = timer()

    single_duration = end_single - start
    parallel_duration = end_parallel - end_single

    print("Single-threaded export took {}".format(single_duration))
    print("Parallel export took {}".format(parallel_duration))

    assert filecmp.cmp(target_single, target_parallel, shallow=False)
    assert single_duration > parallel_duration


if __name__ == "__main__":
    pyunit_utils.standalone_test(export_parallel)
else:
    export_parallel()
