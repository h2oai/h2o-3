

import h2o, tests


def expr_show():
    
    

    iris = h2o.import_file(path=tests.locate("smalldata/iris/iris_wheader.csv"))
    print "iris:"
    iris.show()

    ###################################################################

    # expr[int], expr._data is pending
    res = 2 - iris
    res2 = res[0]
    print "res2:"
    res2.show()

    # expr[int], expr._data is remote
    res3 = res[0]
    print "res3:"
    res3.show()


pyunit_test = expr_show
