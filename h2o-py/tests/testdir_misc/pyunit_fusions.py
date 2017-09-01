import sys

sys.path.insert(1, "../../")
from tests import pyunit_utils

from h2o import H2OFrame
from h2o.expr import ExprNode


def test_fold_fusion_append_expr():
    data = single_column_frame()
    expr = ExprNode("append", ExprNode("append", data, "dummy_1", "col_1"), "dummy_2", "col_2")

    assert expr._op == "append", "Operator name should be `append`"
    assert len(expr._children) == 1 + 2 + 2, "2 append calls should be folded into a single call"
    assert expr._children[1:] == ("dummy_1", "col_1", "dummy_2", "col_2")


def test_fold_fusion_cbind_expr():
    data = single_column_frame()
    expr = ExprNode("cbind", ExprNode("cbind", ExprNode("cbind", data, data), data, data), data)

    assert expr._op == "cbind", "Result operator is still cbind"
    assert len(expr._children) == 5, "Results has 5 arguments"
    assert all([c == data._ex for c in expr._children]), "All arguments are same expression"


def test_fold_fusion_append():
    data = single_column_frame()
    data["col_1"] = 1
    data["col_2"] = 2

    expr = data._ex
    assert expr._op == "append"
    assert len(expr._children) == 1 + 2 + 2
    assert expr._children[1:] == (1, "col_1", 2, "col_2")


def test_fold_fusion_cbind():
    data = single_column_frame()
    data = data.cbind(data).cbind(data).cbind(data)

    expr = data._ex
    assert expr._op == "cbind"
    assert len(expr._children) == 4


def test_skip_fusion_expr():
    data = square_matrix(2)
    expr = ExprNode("cols_py", ExprNode("append", data, "dummy_vec", "dummy_name"), 1)

    assert expr._op == "cols_py"
    assert expr.arg(0) == data._ex and expr.arg(1) == 1


def test_skip_fusion_expr_negative():
    data = square_matrix(2)
    expr = ExprNode("cols_py", ExprNode("append", data, "dummy_vec", "dummy_name"), 33)


    assert expr._op == "cols_py"
    assert expr.arg(0)._op == "append" and expr.arg(1) == 33
    append_expr = expr.arg(0)
    assert append_expr.arg(0) == data._ex


def test_skip_fusion():
    w = 3
    data = square_matrix(w)
    for i in range(w):
        for j in range(w):
            if j > i:
                data["{}_{}".format(i, j)] = data[j] * data[i]

    expr = data._ex
    assert expr._op == "append", "Append operator is used only as root op in the resulting expression"
    assert "append" not in _collect_all_ops(expr)[1:], "Append was elimited from rest of expression"
    assert data.dim == [2,6]


def _collect_all_ops(e):
    return sum([_collect_all_ops(c) for c in e.args() if isinstance(c, ExprNode)], [e._op]) if e.args() else [e._op]

#
# Test fixtures
#
def single_column_frame():
    return H2OFrame(python_obj=[[1], [2], [3], [4], [5]], column_names=["CA"])


def square_matrix(w):
    return H2OFrame((range(0, w), range(0, w)))


__TESTS__ = [test_fold_fusion_append_expr, test_fold_fusion_cbind_expr,
             test_fold_fusion_append, test_fold_fusion_cbind,
             test_skip_fusion_expr, test_skip_fusion_expr_negative,
             test_skip_fusion]

__TESTS__ = [test_skip_fusion]

if __name__ == "__main__":
    for func in __TESTS__:
        pyunit_utils.standalone_test(func)
else:
    for func in __TESTS__:
        func()
