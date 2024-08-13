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
        input_timestamp = '2024-08-02 12:00:00'
        timestamp_df = H2OFrame({"timestamp": input_timestamp})
        h2o.export_file(timestamp_df, path=dir, format="parquet", write_checksum=False)
        h2o.rapids(expr='(setTimeZone "America/Los_Angeles")')
        expected_df = H2OFrame({"timestamp": '2024-08-02 12:00:00'})
        imported_df = h2o.import_file(dir, tz_adjustment=True)
        assert imported_df[0, 0] == expected_df[0, 0]

if __name__ == "__main__":
    pyunit_utils.standalone_test(adjust_timestamp_parquet)
else:
    adjust_timestamp_parquet()
