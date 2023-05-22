import sys, os
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline


def h2oassembly_download_mojo_col_op_string_split():
    values = [["fdsa fsf as", "a.a.a"], 
              ["fda.fsf as", "aa.fa. a"], 
              ["fdsa fsf.as", "ba. .s .sb"],
              ["fdsa f.sf as", "bs.a. .sb"]]
    func = H2OFrame.strsplit
    frame = h2o.H2OFrame(
        python_obj=values,
        column_names=["a", "b"],
        column_types=["string", "string"])
    assembly = H2OAssembly(
        steps=[
            ("col_op1", H2OColOp(op=func, col="a", new_col_name=["x1", "x2"], inplace=False, pattern=" ")),
            ("col_op2", H2OColOp(op=func, col="b", new_col_name=["y1", "y2", "y3"], inplace=False, pattern="\\.")),
        ])
    
    expected = assembly.fit(frame)
    assert_is_type(expected, H2OFrame)
    
    results_dir = os.path.join(os.getcwd(), "results")
    file_name = "h2oassembly_download_mojo_col_op_string_split"
    path = os.path.join(results_dir, file_name + ".mojo")
    
    mojo_file = assembly.download_mojo(file_name=file_name, path=path)
    assert os.path.exists(mojo_file)
    
    pipeline = H2OMojoPipeline(mojo_path=mojo_file)
    result = pipeline.transform(frame)
    assert_is_type(result, H2OFrame)
    
    pyunit_utils.compare_frames(expected, result, expected.nrows, tol_numeric=1e-5)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oassembly_download_mojo_col_op_string_split, init_options={"extra_classpath": ["path_mojo_lib"]})
else:
    h2oassembly_download_mojo_col_op_string_split()
