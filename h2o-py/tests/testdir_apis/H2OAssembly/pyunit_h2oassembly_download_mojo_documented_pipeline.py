import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.transforms.preprocessing import *
from h2o.pipeline import H2OMojoPipeline


def h2oassembly_download_mojo_documented_pipeline():
    frame1 = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    frame2 = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    assembly = H2OAssembly(steps=[
        ("col_select", H2OColSelect(["sepal_len", "petal_len", "species"])),
        ("cos_Sepal.Length", H2OColOp(op=H2OFrame.cos, col="sepal_len", inplace=True)),
        ("str_cnt_Species", H2OColOp(op=H2OFrame.countmatches, col="species", inplace=False, pattern="s"))])

    expected = assembly.fit(frame1)
    assert_is_type(expected, H2OFrame)

    results_dir = os.path.join(os.getcwd(), "results")
    file_name = "h2oassembly_download_mojo_documented_pipeline"
    path = os.path.join(results_dir, file_name + ".mojo")

    mojo_file = assembly.download_mojo(file_name=file_name, path=path)
    assert os.path.exists(mojo_file)

    pipeline = H2OMojoPipeline(mojo_path=mojo_file)
    result = pipeline.transform(frame2)
    print(result)
    assert_is_type(result, H2OFrame)
    pyunit_utils.compare_frames(expected, result, expected.nrows, tol_numeric=1e-5)
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oassembly_download_mojo_documented_pipeline, init_options={"extra_classpath": ["/tmp/mojo/mojo2-runtime-2.7.11.1.jar"]})
else:
    h2oassembly_download_mojo_documented_pipeline()
