import sys
sys.path.insert(1, "../../../")
import h2o

def binop_amp(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    rows, cols = iris.dim()
    iris.show()

    # frame/scaler
    amp_res = 5 & iris
    amp_rows, amp_cols = amp_res.dim()
    assert amp_rows == rows and amp_cols == cols, "dimension mismatch"

    amp_res = iris & 0
    amp_rows, amp_cols = amp_res.dim()
    assert amp_rows == rows and amp_cols == cols, "dimension mismatch"
    for c in range(cols-1):
        for r in range(rows):
            assert not amp_res[c][r].eager(), "value error"

    # vec/vec
    amp_res = iris[0] & iris[1]
    amp_rows = len(amp_res)
    assert amp_rows == rows, "dimension mismatch"

    # frame/vec
    try:
        amp_res = iris & iris[0]
        assert False, "expected error. objects of different dimensions not supported."
    except EnvironmentError:
        pass

    try:
        amp_res = iris[3] & iris
        assert False, "expected error. objects of different dimensions not supported."
    except EnvironmentError:
        pass

    # frame/frame
    amp_res = iris & iris
    amp_rows, amp_cols = amp_res.dim()
    assert amp_rows == rows and amp_cols == cols, "dimension mismatch"

    amp_res = iris[0:2] & iris[1:3]
    amp_rows, amp_cols = amp_res.dim()
    assert amp_rows == rows and amp_cols == 2, "dimension mismatch"

    try:
        amp_res = iris & iris[0:3]
        assert False, "expected error. frames are different dimensions."
    except EnvironmentError:
        pass

if __name__ == "__main__":
  h2o.run_test(sys.argv, binop_amp)

