import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline


def h2oassembly_download_mojo_col_op_time_unary():
    test_time_unary_function(H2OFrame.day)
    test_time_unary_function(H2OFrame.dayOfWeek)
    test_time_unary_function(H2OFrame.hour)
    test_time_unary_function(H2OFrame.minute)
    test_time_unary_function(H2OFrame.second)
    test_time_unary_function(H2OFrame.week)
    test_time_unary_function(H2OFrame.year)
    
    
def test_time_unary_function(function):
    values = [["15.07.09 1:01:34", "a"],
              ["30.09.09 23:00:43", "b"],
              ["3.01.06 13:30:00", "c"],
              ["30.09.09 23:00:12", "d"]]
    frame = h2o.H2OFrame(
        python_obj=values,
        column_names=["dt", "x"],
        column_types=["string", "string"])
    assembly = H2OAssembly(
        steps=[
            ("col_parse_dt", 
             H2OColOp(op=H2OFrame.as_date, col="dt", new_col_name="i", inplace=False, format="%d.%m.%y %H:%M:%S")),
            ("col_op_" + function.__name__,
             H2OColOp(op=function, col="i", new_col_name="o", inplace=False))
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
    pyunit_utils.standalone_test(h2oassembly_download_mojo_col_op_time_unary, init_options={"extra_classpath": ["path_mojo_lib"]})
else:
    h2oassembly_download_mojo_col_op_time_unary()
