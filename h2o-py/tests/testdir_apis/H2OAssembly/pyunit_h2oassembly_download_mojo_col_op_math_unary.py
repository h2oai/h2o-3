import sys, os
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline


def h2oassembly_download_mojo_col_op_math_unary():
    test_unary_math_function(H2OFrame.abs)
    test_unary_math_function(H2OFrame.acos)
    test_unary_math_function(H2OFrame.acosh)
    test_unary_math_function(H2OFrame.asin)
    test_unary_math_function(H2OFrame.asinh)
    test_unary_math_function(H2OFrame.atan)
    test_unary_math_function(H2OFrame.atanh)
    test_unary_math_function(H2OFrame.ceil)
    test_unary_math_function(H2OFrame.cos)
    test_unary_math_function(H2OFrame.cosh)
    test_unary_math_function(H2OFrame.cospi)
    test_unary_math_function(H2OFrame.digamma)
    test_unary_math_function(H2OFrame.exp)
    test_unary_math_function(H2OFrame.expm1)
    test_unary_math_function(H2OFrame.gamma)
    test_unary_math_function(H2OFrame.lgamma)
    test_unary_math_function(H2OFrame.log)
    test_unary_math_function(H2OFrame.log1p)
    test_unary_math_function(H2OFrame.log2)
    test_unary_math_function(H2OFrame.log10)
    test_unary_math_function(H2OFrame.logical_negation)
    test_unary_math_function(H2OFrame.sign)
    test_unary_math_function(H2OFrame.sin)
    test_unary_math_function(H2OFrame.sinh)
    test_unary_math_function(H2OFrame.sinpi)
    test_unary_math_function(H2OFrame.sqrt)
    test_unary_math_function(H2OFrame.tan)
    test_unary_math_function(H2OFrame.tanh)
    test_unary_math_function(H2OFrame.tanpi)
    test_unary_math_function(H2OFrame.trigamma)
    test_unary_math_function(H2OFrame.trunc)
    test_unary_math_function(H2OFrame.round, digits=5)
    test_unary_math_function(H2OFrame.signif, digits=5)
    
    
def test_unary_math_function(function, **params):
    values = [[11, 12.5, "13", 14], [21, 22.2, "23", 24], [31, 32.3, "33", 34]]
    frame = h2o.H2OFrame(
        python_obj=values,
        column_names=["a", "b", "c", "d"],
        column_types=["numeric", "numeric", "string", "numeric"])
    assembly = H2OAssembly(
        steps=[("col_op_" + function.__name__, H2OColOp(op=function, col="b", new_col_name="n", inplace=False, **params)),])
    
    expected = assembly.fit(frame)
    assert_is_type(expected, H2OFrame)
    
    results_dir = os.path.join(os.getcwd(), "results")
    file_name = "h2oassembly_download_mojo_col_op_" + function.__name__
    path = os.path.join(results_dir, file_name + ".mojo")
    
    mojo_file = assembly.download_mojo(file_name=file_name, path=path)
    assert os.path.exists(mojo_file)
    
    pipeline = H2OMojoPipeline(mojo_path=mojo_file)
    result = pipeline.transform(frame)
    assert_is_type(result, H2OFrame)
    
    pyunit_utils.compare_frames(expected, result, expected.nrows, tol_numeric=1e-5)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oassembly_download_mojo_col_op_math_unary, init_options={"extra_classpath": ["path_mojo_lib"]})
else:
    h2oassembly_download_mojo_col_op_math_unary()
