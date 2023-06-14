import sys, os
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline


def h2oassembly_download_mojo_col_op_numeric_conversion():
    values = [[12.5, "13"],
              [12, "23.5"],
              [1.1111, "s"],
              [124, "0.101"]]
    frame = h2o.H2OFrame(
        python_obj=values,
        column_names=["a", "b"],
        column_types=["numeric", "string"])
    test_numeric_conversion_function(H2OFrame.asnumeric, frame)
    values = [["15.07.09 1:01", "15.07.09 1:01"],
              ["30.09.09 23:00", "30.09.09 23:00"],
              ["3.01.06 13:30", "3.01.06 13:30"],
              ["30.09.09 23:00", "3.01.06 13:30"]]
    frame = h2o.H2OFrame(
        python_obj=values,
        column_names=["a", "b"],
        column_types=["string", "string"])
    test_numeric_conversion_function(H2OFrame.as_date, frame, format="%d.%m.%y %H:%M")
    
    
def test_numeric_conversion_function(function, frame,  **params):
    assembly = H2OAssembly(
        steps=[
            ("col_op1_" + function.__name__, H2OColOp(op=function, col="a", new_col_name="x", inplace=False, **params)),
            ("col_op2_" + function.__name__, H2OColOp(op=function, col="b", new_col_name="y", inplace=False, **params))
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
    pyunit_utils.standalone_test(h2oassembly_download_mojo_col_op_numeric_conversion, init_options={"extra_classpath": ["path_mojo_lib"]})
else:
    h2oassembly_download_mojo_col_op_numeric_conversion()
