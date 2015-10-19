



def vec_as_list():
    
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    res = h2o.as_list(iris[0], use_pandas=False)
    assert abs(float(res[4][0]) - 4.6) < 1e-10 and abs(float(res[6][0]) - 5.4) < 1e-10 and \
           abs(float(res[10][0]) - 4.9) < 1e-10, "incorrect values"

    res = 2 - iris
    res2 = h2o.as_list(res[0], use_pandas=False)
    assert abs(float(res2[4][0]) - -2.6) < 1e-10 and abs(float(res2[18][0]) - -3.1) < 1e-10 and \
           abs(float(res2[25][0]) - -2.8) < 1e-10, "incorrect values"

    res3 = h2o.as_list(res[1], use_pandas=False)
    assert abs(float(res3[4][0]) - -1.1) < 1e-10 and abs(float(res3[6][0]) - -1.9) < 1e-10 and \
           abs(float(res3[10][0]) - -1.1) < 1e-10, "incorrect values"


vec_as_list()
