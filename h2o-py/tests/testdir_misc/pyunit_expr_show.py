import sys
sys.path.insert(1, "../../")
import h2o, tests


def expr_show():
    
    

    iris = h2o.import_file(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
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

if __name__ == "__main__":
    tests.run_test(sys.argv, expr_show)
