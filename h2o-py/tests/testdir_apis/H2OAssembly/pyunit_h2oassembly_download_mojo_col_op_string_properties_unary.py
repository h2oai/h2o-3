import sys, os
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline
import uuid


def h2oassembly_download_mojo_col_op_string_properties_unary():
    test_unary_string_properties_function(H2OFrame.countmatches, pattern=["tt", "ex"])
    test_unary_string_properties_function(H2OFrame.entropy)
    test_unary_string_properties_function(H2OFrame.nchar)

    path = os.path.join(os.getcwd(), "results", "h2oassembly_download_mojo_col_op_grep_words")
    with open(path, "w") as text_file:
        text_file.writelines(["33ss33\n", "sssss\n", "tt\n", "33ttaattaas\n", "\n", "asttatta\n", "text\n"])
    test_unary_string_properties_function(H2OFrame.num_valid_substrings, path_to_words=path)
    test_unary_string_properties_function(H2OFrame.grep, pattern="tt", ignore_case=False, invert=False, output_logical=True)
    test_unary_string_properties_function(H2OFrame.grep, pattern="tt", ignore_case=False, invert=True, output_logical=True)
    test_unary_string_properties_function(H2OFrame.grep, pattern="tt", ignore_case=True, invert=False, output_logical=True)
    test_unary_string_properties_function(H2OFrame.grep, pattern="tt", ignore_case=True, invert=True, output_logical=True)

    
def test_unary_string_properties_function(function, **params):
    values = [[12.5, "++&&texTtextText&+", 14],
              [12.2, "  fTtFsaf   ", 24],
              [2.23, "      fd9af ", 34],
              [3.31, "+&texttext&&++", 34],
              [4.31, "3fdsf3", 34],
              [1.13, "+texTText++", 34],
              [52.4, "33", 34],
              [62.5, "ss", 34],
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
    file_name = "h2oassembly_download_mojo_col_op_" + function.__name__ + "_" + str(uuid.uuid4())
    path = os.path.join(results_dir, file_name + ".mojo")
    
    mojo_file = assembly.download_mojo(file_name=file_name, path=path)
    assert os.path.exists(mojo_file)
    
    pipeline = H2OMojoPipeline(mojo_path=mojo_file)
    result = pipeline.transform(frame)
    assert_is_type(result, H2OFrame)
    pyunit_utils.compare_frames(expected, result, expected.nrows, tol_numeric=1e-5)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oassembly_download_mojo_col_op_string_properties_unary, init_options={"extra_classpath": ["path_mojo_lib"]})
else:
    h2oassembly_download_mojo_col_op_string_properties_unary()
