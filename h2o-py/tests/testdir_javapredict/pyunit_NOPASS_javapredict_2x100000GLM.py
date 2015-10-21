import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def javapredict_2x100000():

    # optional parameters
    params = {"max_iterations":1, "solver":"L_BFGS"}
    print "Parameter list:"
    for k,v in zip(params.keys(), params.values()): print "{0}, {1}".format(k,v)

    train = h2o.import_file(pyunit_utils.locate("smalldata/jira/2x100000_real.csv.gz"))
    test = train
    x = range(1,train.ncol)
    y = 0

    pyunit_utils.javapredict("glm", "numeric", train, test, x, y, **params)



if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_2x100000)
else:
    javapredict_2x100000()
