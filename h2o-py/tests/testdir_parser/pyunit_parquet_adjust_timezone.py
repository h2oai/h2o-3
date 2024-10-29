import sys

sys.path.insert(1, "../../../")
from tests import pyunit_utils
from h2o.frame import H2OFrame
from datetime import datetime, timezone, timedelta
import tempfile
import h2o

'''
Adjust timestamp parquet
'''

test_local_user_timezone = "America/Chicago"
time_format = '%Y-%m-%d %H:%M:%S'


def adjust_timestamp_parquet():
    with tempfile.TemporaryDirectory() as dir:
        # prepare the file which will be imported
        input_timestamp = '2024-08-02 12:00:00'
        original_timestamp_df = H2OFrame({"timestamp": input_timestamp})
        h2o.export_file(original_timestamp_df, path=dir + "/import", format="parquet", write_checksum=False)

        # import the file and see tz_adjust_to_local works
        imported_df = h2o.import_file(dir + "/import", tz_adjust_to_local=True)
        expected_timestamp = datetime.strptime(input_timestamp, time_format).replace(tzinfo=timezone.utc)
        expected_df = H2OFrame({"timestamp": expected_timestamp.astimezone().strftime(time_format)})
        assert imported_df[0, 0] == expected_df[0, 0]

        # export the file and see tz_adjust_from_local works
        h2o.export_file(imported_df, path=dir + "/export", tz_adjust_from_local=True)
        reimported_without_adjustment_df = h2o.import_file(dir + "/import")
        assert original_timestamp_df[0, 0] == reimported_without_adjustment_df[0, 0]


if __name__ == "__main__":
    pyunit_utils.standalone_test(adjust_timestamp_parquet)
else:
    adjust_timestamp_parquet()
