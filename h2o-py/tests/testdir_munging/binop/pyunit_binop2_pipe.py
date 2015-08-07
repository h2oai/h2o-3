import sys
sys.path.insert(1, "../../../")
import h2o

def binop_pipe(ip,port):
    # Connect to h2o
    

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    rows, cols = iris.dim()
    iris.show()

    # frame/scaler
    res = 5 | iris
    rows, cols = res.dim()
    assert rows == rows and cols == cols, "dimension mismatch"

    res = iris | 1
    rows, cols = res.dim()
    assert rows == rows and cols == cols, "dimension mismatch"

    # vec/vec
    res = iris[0] | iris[1]
    rows = len(res)
    assert rows == rows, "dimension mismatch"

    # vec/scaler
    res = iris[0] | 1
    rows = res.nrow()
    assert rows == rows, "dimension mismatch"
    new_rows = iris[res].nrow()
    assert new_rows == rows, "wrong number of rows returned"

    res = 1 | iris[1]
    rows = res.nrow()
    assert rows == rows, "dimension mismatch"
    new_rows = iris[res].nrow()
    assert new_rows == rows, "wrong number of rows returned"

    # frame/vec
    #try:
    #    res = iris | iris[0]
    #    res.show()
    #    assert False, "expected error. objects of different dimensions not supported."
    #except EnvironmentError:
    #    pass

    #try:
    #    res = iris[3] | iris
    #    res.show()
    #    assert False, "expected error. objects of different dimensions not supported."
    #except EnvironmentError:
    #    pass

    # frame/frame
    res = iris | iris
    rows, cols = res.dim()
    assert rows == rows and cols == cols, "dimension mismatch"

    res = iris[0:2] | iris[1:3]
    rows, cols = res.dim()
    assert rows == rows and cols == 2, "dimension mismatch"

    #try:
    #    res = iris | iris[0:3]
    #    res.show()
    #    assert False, "expected error. frames are different dimensions."
    #except EnvironmentError:
    #    pass

if __name__ == "__main__":
  h2o.run_test(sys.argv, binop_pipe)


