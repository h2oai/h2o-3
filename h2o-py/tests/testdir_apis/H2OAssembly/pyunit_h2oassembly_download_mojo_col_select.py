import sys, os
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline

def h2oassembly_download_mojo_col_select():
    values = [[11, 12, "13", 14], [21, 22, "23", 24], [31, 32, "33", 34]]
    frame = h2o.H2OFrame(
        python_obj=values,
        column_names=["a", "b", "c", "d"],
        column_types=["numeric", "numeric", "string", "numeric"])
    assembly = H2OAssembly(steps=[("col_select", H2OColSelect(["b", "c"])),])

    expected = assembly.fit(frame)
    assert_is_type(expected, H2OFrame)

    results_dir = os.path.join(os.getcwd(), "results")
    file_name = "h2oassembly_download_mojo_col_select"
    path = os.path.join(results_dir, file_name + ".mojo")
    
    mojo_file = assembly.download_mojo(file_name=file_name, path=path)
    assert os.path.exists(mojo_file)
    
    pipeline = H2OMojoPipeline(mojo_path=mojo_file)
    result = pipeline.transform(frame)
    assert_is_type(result, H2OFrame)

    pyunit_utils.compare_frames(expected, result, expected.nrows, tol_numeric=1e-5)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oassembly_download_mojo_col_select, init_options={"extra_classpath": ["path_mojo_lib"]})
else:
    h2oassembly_download_mojo_col_select()
