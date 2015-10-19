

import h2o, tests

def ls_test():
    
    

    iris = h2o.import_file(path=tests.locate("smalldata/iris/iris.csv"))

    h2o.ls()


pyunit_test = ls_test
