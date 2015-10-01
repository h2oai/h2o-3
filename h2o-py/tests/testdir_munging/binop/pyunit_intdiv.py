import sys
sys.path.insert(1, "../../../")
import h2o, tests

def intdiv():

    iris = h2o.import_file(path=tests.locate("smalldata/iris/iris_wheader.csv"))
    iris = iris[:,0:4]
    rows, cols = iris.dim

    #frame/scaler
    res = iris // 5
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    res = 5 // iris[:,0:2]
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == 2, "dimension mismatch"

    #frame/col
    try:
        res = iris // iris[0]
        res.show()
        assert False, "expected error. objects of different dimensions not supported."
    except EnvironmentError:
        pass

    try:
      res = iris[2] // iris
      res.show()
      assert False, "expected error. objects of different dimensions not supported."
    except EnvironmentError:
      pass

    #col/col
    res = iris[0] // iris[1]
    res.show()

    res = iris[1] // iris[2] // iris[1]
    res.show()

    #col/scaler
    res = iris[0] // 5
    res.show()

    # frame/frame
    res = iris[:,0:2] // iris[:,0:2]
    res_rows, res_cols = res.dim
    assert res_rows == 150 and res_cols == 2, "dimension mismatch"

    try:
      res = iris[:,0:2] // iris[:,0:1]
      res.show()
      assert False, "expected error. frames are different dimensions."
    except EnvironmentError:
      pass

if __name__ == "__main__":
    tests.run_test(sys.argv, intdiv)

