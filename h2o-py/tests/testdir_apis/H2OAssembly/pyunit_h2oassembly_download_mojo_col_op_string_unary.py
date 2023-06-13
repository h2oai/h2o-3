import sys, os
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline


def h2oassembly_download_mojo_col_op_string_unary():
    test_unary_string_function(H2OFrame.lstrip, set='+&')
    test_unary_string_function(H2OFrame.rstrip, set='+&')
    test_unary_string_function(H2OFrame.gsub, pattern="tt", replacement="bbcc", ignore_case=True)
    test_unary_string_function(H2OFrame.sub, pattern="tt", replacement="bbcc", ignore_case=True)
    test_unary_string_function(H2OFrame.substring, start_index=2, end_index=8)
    test_unary_string_function(H2OFrame.tolower)
    test_unary_string_function(H2OFrame.toupper)
    test_unary_string_function(H2OFrame.trim)

    
    
def test_unary_string_function(function, **params):
    values = [[12.5, "++&&texTtextText&+", 14],
              [12.2, "  fTFsaf   ", 24],
              [2.23, "      fd9af ", 34],
              [3.31, "+&texttext&&++", 34],
              [4.31, "3fdsf3", 34],
              [1.13, "+texTText++", 34],
              [52.4, "33  ", 34],
              [62.5, "s", 34],
              [82.6, "&&texTtexttext&", 34],
              [12.8, "ttaatt", 34],
              [35.9, "asttatta", 34],
              [32.3, "", 34]]
    frame = h2o.H2OFrame(
        python_obj=values,
        column_names=["a", "s", "c"],
        column_types=["numeric", "string", "numeric"])
    assembly = H2OAssembly(
        steps=[("col_op_" + function.__name__, H2OColOp(op=function, col="s", new_col_name="n", inplace=False, **params)),])
    
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
    pyunit_utils.standalone_test(h2oassembly_download_mojo_col_op_string_unary, init_options={"extra_classpath": ["path_mojo_lib"]})
else:
    h2oassembly_download_mojo_col_op_string_unary()
