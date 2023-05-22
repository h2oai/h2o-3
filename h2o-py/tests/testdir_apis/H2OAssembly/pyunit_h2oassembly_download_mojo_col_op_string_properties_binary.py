import sys, os
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline


def h2oassembly_download_mojo_col_op_string_properties_binary():
    test_binary_string_properties_function(H2OFrame.strdistance, measure='lv', compare_empty=True)
    test_binary_string_properties_function(H2OFrame.strdistance, measure='lcs', compare_empty=True)
    test_binary_string_properties_function(H2OFrame.strdistance, measure='lv', compare_empty=False)
    test_binary_string_properties_function(H2OFrame.strdistance, measure='lcs', compare_empty=False)
    
    
def test_binary_string_properties_function(function, **params):
    values = [[12.5, "++&&texTtextText&+", 14, "fsadf"],
              [12.2, "  fTFsaf   ", 24, "fdsfsda"],
              [2.23, "      fd9af ", 34, "fdsfsd"],
              [3.31, "+&texttext&&++", 34, ""],
              [4.31, "3fdsf3", 34, "fdsa"],
              [1.13, "+texTText++", 34, "h"],
              [52.4, "33  ", 34,  "33"],
              [62.5, "s", 34, "s"],
              [82.6, "&&texTtexttext&", 34, "text"],
              [12.8, "ttaatt", 34, "ttaa"],
              [35.9, "asttatta", 34, "atta"],
              [32.3, "", 34, "d"]]
    frame = h2o.H2OFrame(
        python_obj=values,
        column_names=["a", "sl", "c", "sr"],
        column_types=["numeric", "string", "numeric", "string"])
    assembly = H2OAssembly(steps=[
       ("col_op_" + function.__name__,
        H2OBinaryOp(op=function, col="sl", right=H2OCol("sr"), new_col_name="n", inplace=False, **params)),
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
    pyunit_utils.standalone_test(h2oassembly_download_mojo_col_op_string_properties_binary, init_options={"extra_classpath": ["path_mojo_lib"]})
else:
    h2oassembly_download_mojo_col_op_string_properties_binary()
