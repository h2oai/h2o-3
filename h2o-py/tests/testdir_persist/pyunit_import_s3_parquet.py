import h2o
import os

from h2o.persist import set_s3_credentials

from tests import pyunit_utils
from pandas.testing import assert_frame_equal


def test_import_parquet_from_s3():
    access_key_id = os.environ['AWS_ACCESS_KEY_ID']
    secret_access_key = os.environ['AWS_SECRET_ACCESS_KEY']
    assert access_key_id is not None
    assert secret_access_key is not None
    set_s3_credentials(access_key_id, secret_access_key)
    
    from_s3 = h2o.import_file("s3://h2o-public-test-data/smalldata/parser/parquet/airlines-simple.snappy.parquet")
    from_local = h2o.import_file(pyunit_utils.locate("smalldata/parser/parquet/airlines-simple.snappy.parquet"))

    print(from_s3.summary())

    assert from_s3.shape == (24421, 12)
    assert_frame_equal(from_local.as_data_frame(), from_s3.as_data_frame())


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_import_parquet_from_s3)
else:
    test_import_parquet_from_s3()
