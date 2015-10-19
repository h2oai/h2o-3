

import h2o, tests

def javapredict_2x100000():

    # optional parameters
    params = {"max_iterations":1, "solver":"L_BFGS"}
    print "Parameter list:"
    for k,v in zip(params.keys(), params.values()): print "{0}, {1}".format(k,v)

    train = h2o.import_file(tests.locate("smalldata/jira/2x100000_real.csv.gz"))
    test = train
    x = range(1,train.ncol)
    y = 0

    tests.javapredict("glm", "numeric", train, test, x, y, **params)


pyunit_test = javapredict_2x100000
