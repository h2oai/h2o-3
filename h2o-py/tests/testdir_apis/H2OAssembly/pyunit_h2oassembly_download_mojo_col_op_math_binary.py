import sys, os
from functools import *

sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline


def h2oassembly_download_mojo_col_op_math_binary():
    test_binary_math_function(H2OFrame.__add__)
    test_binary_math_function(H2OFrame.__sub__)
    test_binary_math_function(H2OFrame.__mul__)
    test_binary_math_function(H2OFrame.__div__)
    test_binary_math_function(H2OFrame.__le__)
    test_binary_math_function(H2OFrame.__lt__)
    test_binary_math_function(H2OFrame.__ge__)
    test_binary_math_function(H2OFrame.__gt__)
    test_binary_math_function(H2OFrame.__eq__)
    test_binary_math_function(H2OFrame.__ne__)
    test_binary_math_function(H2OFrame.__pow__)
    test_binary_math_function(H2OFrame.__mod__)
    test_binary_math_function(H2OFrame.__and__)
    test_binary_math_function(H2OFrame.__or__)
    test_binary_math_function(H2OFrame.__floordiv__)
    
    
def test_binary_math_function(function):
    values = [[11, 12.5, "13", 14], [21, 22.2, "23", 24], [31, 32.3, "33", 34]]
    frame = h2o.H2OFrame(
        python_obj=values,
        column_names=["a", "b", "c", "d"],
        column_types=["numeric", "numeric", "string", "numeric"])
    function_name = function.__name__.strip('_')
    assembly = H2OAssembly(steps=[
        (function_name + "1", H2OBinaryOp(op=function, col="b", right=H2OCol("d"), new_col_name="n1", inplace=False)),
        (function_name + "2", H2OBinaryOp(op=function, col="b", right=5.0, new_col_name="n2", inplace=False)),
        (function_name + "3", H2OBinaryOp(op=function, col="b", left=5.0, new_col_name="n3", inplace=False)),
    ])
    
    expected = assembly.fit(frame)
    assert_is_type(expected, H2OFrame)
    
    results_dir = os.path.join(os.getcwd(), "results")
    file_name = "h2oassembly_download_mojo_col_op_" + function_name
    path = os.path.join(results_dir, file_name + ".mojo")
    
    mojo_file = assembly.download_mojo(file_name=file_name, path=path)
    assert os.path.exists(mojo_file)
    
    pipeline = H2OMojoPipeline(mojo_path=mojo_file)
    result = pipeline.transform(frame)
    assert_is_type(result, H2OFrame)
    
    pyunit_utils.compare_frames(expected, result, expected.nrows, tol_numeric=1e-5)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oassembly_download_mojo_col_op_math_binary, init_options={"extra_classpath": ["path_mojo_lib"]})
else:
    h2oassembly_download_mojo_col_op_math_binary()
