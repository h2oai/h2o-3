



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


javapredict_2x100000()
