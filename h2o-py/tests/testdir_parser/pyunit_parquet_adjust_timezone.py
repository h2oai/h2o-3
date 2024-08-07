import sys

sys.path.insert(1, "../../../")
from tests import pyunit_utils
from h2o.frame import H2OFrame
import tempfile
import h2o

'''
Adjust timestamp parquet
'''

def adjust_timestamp_parquet():
    with tempfile.TemporaryDirectory() as dir:
        timestamp_df = H2OFrame({"timestamp": '2024-06-06 06:06:06'})
        h2o.export_file(timestamp_df, path=dir, format="parquet", write_checksum=False)
        imported_df = h2o.import_file(dir)
        expected_df = H2OFrame({"timestamp": '2024-06-06 08:06:06'})
    pyunit_utils.compare_frames(expected_df, imported_df, numElements=1)
        
if __name__ == "__main__":
    pyunit_utils.standalone_test(adjust_timestamp_parquet, init_options={"jvm_custom_args": ["-Dsys.ai.h2o.parquet.import.timestamp.adjustment=2"]})
else:
    adjust_timestamp_parquet()
