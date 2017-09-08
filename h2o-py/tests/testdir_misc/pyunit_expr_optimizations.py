import h2o
import sys

sys.path.insert(1, "../../")
from tests import pyunit_utils

from h2o import H2OFrame
from h2o.expr import ExprNode


def _assert_expr_results_eq(expr_provider, skip_expr_assert=False):
    flag = h2o.is_expr_optimizations_enabled()
    try:
        # Get result of optimized expression
        h2o.enable_expr_optimizations(True)
        opt_expr = expr_provider()
        opt_result = H2OFrame._expr(opt_expr)
        # Get result of full expression
        h2o.enable_expr_optimizations(False)
        noopt_expr = expr_provider()
        noopt_result = H2OFrame._expr(noopt_expr)
        if not skip_expr_assert:
            assert opt_expr._debug_print() != noopt_expr._debug_print(), "The optimization should simplify expression!"
        assert noopt_result.as_data_frame(use_pandas=False) == opt_result.as_data_frame(
            use_pandas=False), "Results with/without expression optimization should match!"
        return opt_expr, noopt_expr
    finally:
        h2o.enable_expr_optimizations(flag)


def test_fold_optimization_append_expr():
    data_dst = single_column_frame()
    data_src = single_column_frame()

    def get_expr():
        return ExprNode("append", ExprNode("append", data_dst, data_src, "COL_1"), data_src,
                        "COL_2")

    (expr, _) = _assert_expr_results_eq(get_expr)

    assert expr._op == "append", "Operator name should be `append`"
    assert len(expr._children) == 1 + 2 + 2, "2 append calls should be folded into a single call"
    assert expr._children[1:] == (data_src._ex, "COL_1", data_src._ex, "COL_2")


def test_fold_optimization_cbind_expr():
    data = single_column_frame()

    def get_expr():
        return ExprNode("cbind", ExprNode("cbind", ExprNode("cbind", data, data), data, data), data)

    (expr, _) = _assert_expr_results_eq(get_expr)

    assert expr._op == "cbind", "Result operator is still cbind"
    assert len(expr._children) == 5, "Results has 5 arguments"
    assert all([c == data._ex for c in expr._children]), "All arguments are same expression"


def test_fold_optimization_rbind_expr():
    data0 = square_matrix(3, 0)
    data1 = square_matrix(3, 1)
    data2 = square_matrix(3, 2)

    def get_expr():
        return ExprNode("rbind", ExprNode("rbind", ExprNode("rbind", data0, data1), data0, data1),
                        data2)

    (expr, _) = _assert_expr_results_eq(get_expr)

    assert expr._op == "rbind", "Result operator is still cbind"
    assert len(expr._children) == 5, "Results has 5 arguments"

    fr = H2OFrame._expr(expr)
    assert fr.dim == [15, 3]
    assert fr.as_data_frame(use_pandas=False, header=False) == [['0'] * 3, ['0'] * 3, ['0'] * 3,
                                                                ['1'] * 3, ['1'] * 3, ['1'] * 3,
                                                                ['0'] * 3, ['0'] * 3, ['0'] * 3,
                                                                ['1'] * 3, ['1'] * 3, ['1'] * 3,
                                                                ['2'] * 3, ['2'] * 3, ['2'] * 3]


def test_fold_optimization_append():
    assert h2o.is_expr_optimizations_enabled(), "Expression optimization needs to be enabled"

    data = single_column_frame()
    data["col_1"] = 1
    data["col_2"] = 2

    expr = data._ex
    assert expr._op == "append"
    assert len(expr._children) == 1 + 2 + 2
    assert expr._children[1:] == (1, "col_1", 2, "col_2")


def test_fold_optimization_cbind():
    assert h2o.is_expr_optimizations_enabled(), "Expression optimization needs to be enabled"

    data = single_column_frame()
    data = data.cbind(data).cbind(data).cbind(data)

    expr = data._ex
    assert expr._op == "cbind"
    assert len(expr._children) == 4


def test_skip_optimization_expr():
    src_vec = single_column_frame()
    data = square_matrix(src_vec.nrow)

    def get_expr():
        return ExprNode("cols_py", ExprNode("append", data, src_vec, "dummy_name"), 1)

    (expr, _) = _assert_expr_results_eq(get_expr)

    assert expr._op == "cols_py"
    assert expr.arg(0) == data._ex and expr.arg(1) == 1


def test_skip_optimization_expr_negative():
    src_vec = single_column_frame()
    data = square_matrix(src_vec.nrow)

    def get_expr():
        return ExprNode("cols_py", ExprNode("append", data, src_vec, "dummy_name"), src_vec.nrow)

    (expr, _) = _assert_expr_results_eq(get_expr, skip_expr_assert=True)

    assert expr._op == "cols_py"
    assert expr.arg(0)._op == "append" and expr.arg(1) == src_vec.nrow
    append_expr = expr.arg(0)
    assert append_expr.arg(0) == data._ex


def test_skip_optimization():
    assert h2o.is_expr_optimizations_enabled(), "Expression optimization needs to be enabled"
    w = 3
    data = square_matrix(w)
    for i in range(w):
        for j in range(w):
            if j > i:
                data["{}_{}".format(i, j)] = data[j] * data[i]

    expr = data._ex
    assert expr._op == "append", "Append operator is used only as root op in the resulting expression"
    assert "append" not in _collect_all_ops(expr)[
                           1:], "Append was eliminated from rest of expression"
    assert data.dim == [w, 6]


def _collect_all_ops(e):
    return sum([_collect_all_ops(c) for c in e.args() if isinstance(c, ExprNode)],
               [e._op]) if e.args() else [e._op]


#
# Test fixtures
#
def single_column_frame():
    return H2OFrame(python_obj=[[1], [2], [3], [4], [5]], column_names=["CA"])


def square_matrix(w, cell_value=None):
    row = [cell_value] * w if cell_value is not None else range(0, w)
    return H2OFrame(python_obj = [list(row) for i in range(0, w)])


__TESTS__ = [test_fold_optimization_append_expr, test_fold_optimization_cbind_expr,
             test_fold_optimization_append, test_fold_optimization_cbind,
             test_fold_optimization_rbind_expr,
             test_skip_optimization_expr, test_skip_optimization_expr_negative,
             test_skip_optimization]

if __name__ == "__main__":
    for func in __TESTS__:
        pyunit_utils.standalone_test(func)
else:
    for func in __TESTS__:
        func()
