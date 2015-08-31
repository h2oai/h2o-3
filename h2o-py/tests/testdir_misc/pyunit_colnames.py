import sys
sys.path.insert(1, "../../")
import h2o, tests

def col_names_check(ip,port):
    
    

    iris_wheader = h2o.import_file(h2o.locate("smalldata/iris/iris_wheader.csv"))
    assert iris_wheader.col_names == ["sepal_len","sepal_wid","petal_len","petal_wid","class"], \
        "Expected {0} for column names but got {1}".format(["sepal_len","sepal_wid","petal_len","petal_wid","class"],
                                                           iris_wheader.col_names)

    iris = h2o.import_file(h2o.locate("smalldata/iris/iris.csv"))
    assert iris.col_names == ["C1","C2","C3","C4","C5"], "Expected {0} for column names but got " \
                                                           "{1}".format(["C1","C2","C3","C4","C5"], iris.col_names)

if __name__ == "__main__":
    tests.run_test(sys.argv, col_names_check)
