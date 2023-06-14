from builtins import zip
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def javapredict_drf_xlarge():
    # Should produce ~800MB POJO
    params = {'ntrees': 20, 'max_depth': 35, 'min_rows': 1}
    for k, v in zip(list(params.keys()), list(params.values())):
        print("{0}, {1}".format(k,v))

    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/higgs_head_2M.csv"))
    test = train[list(range(0,10)), :]
    x = list(range(1, train.ncol))
    y = 0

    pyunit_utils.javapredict("random_forest", "numeric", train, test, x, y, **params)


if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_drf_xlarge)
else:
    javapredict_drf_xlarge()
