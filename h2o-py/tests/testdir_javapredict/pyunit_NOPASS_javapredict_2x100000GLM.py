import sys
sys.path.insert(1, "../../")
import h2o, tests

def javapredict_2x100000():

    # optional parameters
    params = {"max_iterations":1, "solver":"L_BFGS"}
    print "Parameter list:"
    for k,v in zip(params.keys(), params.values()): print "{0}, {1}".format(k,v)

    train = h2o.import_file(tests.locate("smalldata/jira/2x100000_real.csv"))
    test = train
    x = range(1,train.ncol)
    y = 0

    tests.javapredict("glm", "numeric", train, test, x, y, **params)

if __name__ == "__main__":
    tests.run_test(sys.argv, javapredict_2x100000)
