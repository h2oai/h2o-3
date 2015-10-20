



def ls_test():
    
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    h2o.ls()


ls_test()
