from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import string
import os
import random
import pandas as pd
from pandas.testing import assert_frame_equal
import uuid


def test_export_import_date_empty_in_date():
    data = {"datetime_str": ["2025-09-01 12:34:56", "NaN"],
            "numb": ["1", "2"]}
    hf = h2o.H2OFrame(data)
    print(hf)
    path = pyunit_utils.locate("results")
    exported_filepath = os.path.join(path, id_generator() + "parquet_empty_date")

    print(exported_filepath)

    h2o.export_file(hf, exported_filepath, format="parquet")
    assert os.path.isdir(exported_filepath)

    imported_file = h2o.import_file(exported_filepath, "part-m-")
    assert_frame_equal(imported_file.as_data_frame(True), hf.as_data_frame(True), check_column_type=True)


def test_export_uuid_not_supported():
    try:
        data = {"name": ["Alice", "Bob", "Charlie"]}
        hf = h2o.H2OFrame(data)
        uuids = [str(uuid.uuid4()) for _ in range(hf.nrows)]
        uuid_df = pd.DataFrame({"uuid": uuids})
        uuid_hf = h2o.H2OFrame(uuid_df)
        hf = hf.cbind(uuid_hf)
        print(hf)
        assert hf.types["uuid"] == "uuid", "No UUID column type. We need uuid type for this testcase"
        path = pyunit_utils.locate("results")
        exported_filepath = os.path.join(path, id_generator() + "parquet_uuid")

        print(exported_filepath)

        h2o.export_file(hf, exported_filepath, format="parquet")
        assert False, "Test should raise exception"
    except Exception as e:
        assert "UUID column type is not supported." in str(e)


def id_generator(size=6, chars=string.ascii_uppercase + string.digits):
    return ''.join(random.choice(chars) for _ in range(size))


pyunit_utils.run_tests([
    test_export_import_date_empty_in_date,
    test_export_uuid_not_supported
])
