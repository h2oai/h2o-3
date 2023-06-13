import sys, os
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline


def h2oassembly_download_mojo_string_conversion_function():
    test_string_conversion_function(H2OFrame.ascharacter)
    test_string_conversion_function(H2OFrame.asfactor)
    
    
def test_string_conversion_function(function):
    values = [[12.5, "fdsa", "aa"], 
              [22.2, "f343", "aa"], 
              [32.3, "4323", "bb"],
              [-2.3, "3223", "bb"]]
    frame = h2o.H2OFrame(
        python_obj=values,
        column_names=["a", "b", "c"],
        column_types=["numeric", "string", "enum"])
    assembly = H2OAssembly(
        steps=[
            ("col_op1_" + function.__name__, H2OColOp(op=function, col="a", new_col_name="x", inplace=False)),
            ("col_op2_" + function.__name__, H2OColOp(op=function, col="b", new_col_name="y", inplace=False)),
            ("col_op3_" + function.__name__, H2OColOp(op=function, col="c", new_col_name="z", inplace=False))
        ])
    
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
    pyunit_utils.standalone_test(h2oassembly_download_mojo_string_conversion_function, init_options={"extra_classpath": ["path_mojo_lib"]})
else:
    h2oassembly_download_mojo_string_conversion_function()
