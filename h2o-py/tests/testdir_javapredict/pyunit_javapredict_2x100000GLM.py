from __future__ import print_function
from builtins import zip
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def javapredict_2x100000():

    # optional parameters
    params = {"max_iterations":1, "solver":"L_BFGS"}
    print("Parameter list:")
    for k,v in zip(list(params.keys()), list(params.values())): print("{0}, {1}".format(k,v))

    train = h2o.import_file(pyunit_utils.locate("smalldata/jira/2x100000_real.csv.gz"))
    test = train
    x = list(range(1,train.ncol))
    y = 0

    pyunit_utils.javapredict(algo="glm", equality="numeric", train=train, test=test, x=x, y=y, compile_only=True, **params)



if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_2x100000)
else:
    javapredict_2x100000()
