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

    # frame/vec
    amp_res = iris[0] & iris
    amp_rows, amp_cols = amp_res.dim()
    assert amp_rows == rows and amp_cols == cols, "dimension mismatch"


    amp_res = iris & (iris[0] >= 5)
    amp_rows, amp_cols = amp_res.dim()
    assert amp_rows == rows and amp_cols == cols, "dimension mismatch"
    amp_res.show()
    assert  amp_res[amp_res[0] > 0].nrow() == 128, "wrong number of rows returned"

    # frame/frame
    amp_res = iris & iris
    amp_rows, amp_cols = amp_res.dim()
    assert amp_rows == rows and amp_cols == cols, "dimension mismatch"

    amp_res = iris & iris[0:3]
    amp_rows, amp_cols = amp_res.dim()
    assert amp_rows == rows and amp_cols == 3, "dimension mismatch"
  
if __name__ == "__main__":
  h2o.run_test(sys.argv, binop_amp)

