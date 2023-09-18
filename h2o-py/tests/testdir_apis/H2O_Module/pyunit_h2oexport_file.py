import os
import shutil
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils as pu
import h2o

def export_file_csv():
    """
    Python API test: h2o.export_file(frame, path, force=False, parts=1).  Note that force=True is only honored if
    parts=1.  Otherwise, an error will be thrown.
    """
    training_data = h2o.import_file(pu.locate("smalldata/logreg/benign.csv"))
    try:
        results_dir = pu.locate("results")    # find directory path to results folder
        final_path = os.path.join(results_dir, 'frameData')
        h2o.export_file(training_data, final_path, force=True, parts=1)       # save data
        assert os.path.isfile(final_path), "h2o.export_file() command is not working."
        final_dir_path = os.path.join(results_dir, 'multiFrame')
        h2o.export_file(training_data, final_dir_path, force=True, parts=-1)
        assert len(os.listdir(final_dir_path))>0, "h2o.export_file() command is not working."
    except Exception as e:
        if e.__class__.__name__=='ValueError' and 'File not found' in e.args[0]:
            print("Directory is not writable.  h2o.export_file() command is not tested.")
        else :
            assert e.__class__.__name__=='H2OResponseError' and \
                   'exportFrame: Cannot use path' in e.args[0]._props['dev_msg'], \
                "h2o.export_file() command is not working."
            print("Directory: {0} is not empty.  Delete or empy it before re-run.  h2o.export_file() "
                  "is not tested with multi-part export.".format(final_dir_path))


def export_file_parquet():
    data = h2o.import_file(pu.locate("smalldata/titanic/titanic_expanded.csv"), header=1)
    path = pu.locate("results")
    export_dir = os.path.join(path, data.frame_id + "_export_parquet")
    if os.path.isdir(export_dir):
        shutil.rmtree(export_dir, ignore_errors=True)
    h2o.export_file(data, path=export_dir, format='parquet')
    
    assert os.path.isdir(export_dir)
    assert any(os.path.splitext(f)[1] == '.crc' for f in os.listdir(export_dir))


def export_file_parquet_no_checksum():
    data = h2o.import_file(pu.locate("smalldata/titanic/titanic_expanded.csv"), header=1)
    path = pu.locate("results")
    export_dir = os.path.join(path, data.frame_id + "_export_parquet_no_checksum")
    if os.path.isdir(export_dir):
        shutil.rmtree(export_dir, ignore_errors=True)
    h2o.export_file(data, path=export_dir, format='parquet', write_checksum=False)

    assert os.path.isdir(export_dir)
    assert os.listdir(export_dir)
    assert not any(os.path.splitext(f)[1] == '.crc' for f in os.listdir(export_dir))


pu.run_tests([
    export_file_csv,
    export_file_parquet,
    export_file_parquet_no_checksum
])
