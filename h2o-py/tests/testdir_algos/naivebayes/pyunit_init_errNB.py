

import h2o, tests

def nb_init_err():
    

    print "Importing iris_wheader.csv data...\n"
    iris = h2o.upload_file(tests.locate("smalldata/iris/iris_wheader.csv"))
    iris.describe

    print "Laplace smoothing parameter is negative"
    try:
        h2o.naive_bayes(x=iris[0:4], y=iris[4], laplace=-1)
        assert False, "Expected naive bayes algo to fail on negative laplace training parameter"
    except:
        pass

    print "Minimum standard deviation is zero"
    try:
        h2o.naive_bayes(x=iris[0:4], y=iris[4], min_sdev=0)
        assert False, "Expected naive bayes algo to fail on min_sdev = 0"
    except:
        pass

    print "Response column is not categorical"
    try:
        h2o.naive_bayes(x=iris[0:3], y=iris[3], min_sdev=0)
        assert False, "Expected naive bayes algo to fail on response not categorical"
    except:
        pass


pyunit_test = nb_init_err
