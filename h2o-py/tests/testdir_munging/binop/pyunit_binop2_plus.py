import sys
sys.path.insert(1, "../../../")
import h2o

def binop_plus(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    rows, cols = iris.dim()
    iris.show()

    #frame/scaler
    res = iris + 5
    res_rows, res_cols = res.dim()
    h2o_sums = []
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    for c in range(cols-1):
        col_sum = 0
        for r in range(rows):
            col_sum = col_sum + res[c][r].eager()
        h2o_sums.append(col_sum)
    for x, y in zip(h2o_sums, [1626.5, 1208.1, 1313.8, 929.8]):
        assert abs(x - y) < 1e-7,  "unexpected column sums."

    res = 5 + iris
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    #frame/vec
    try:
        res = iris + iris[0]
        assert False, "expected error. objects of different dimensions not supported."
    except EnvironmentError:
        pass

    try:
        res = iris[2] + iris
        assert False, "expected error. objects of different dimensions not supported."
    except EnvironmentError:
        pass

    #vec/vec
    res = iris[0] + iris[1]
    res.show()
    assert abs(sum([res[i].eager() for i in range(150)]) - 1334.6) < 1e-7, "expected different sum"

    res = iris[0] + iris[1] + iris[2] + iris[3]
    res.show()
    assert abs(sum([res[i].eager() for i in range(150)]) - 2078.2) < 1e-7, "expected different sum"

    # frame/frame
    res = iris + iris
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    res = iris[0:2] + iris[1:3]
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == 2, "dimension mismatch"

    try:
        res = iris + iris[0:3]
        assert False, "expected error. frames are different dimensions."
    except EnvironmentError:
        pass

if __name__ == "__main__":
    h2o.run_test(sys.argv, binop_plus)