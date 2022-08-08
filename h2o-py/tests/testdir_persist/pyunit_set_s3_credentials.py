import h2o
import os

from h2o.exceptions import H2OValueError
from h2o.persist import set_s3_credentials
from h2o.persist import remove_s3_credentials

from tests import pyunit_utils


def test_set_s3_credentials():
    try:
        test_set_s3_credentials_impl()
    finally:
        remove_s3_credentials()


def test_set_s3_credentials_impl():
    aws_creds_prefix = os.environ['AWS_CREDS_PREFIX'] if 'AWS_CREDS_PREFIX' in os.environ else ''
    access_key_id = os.environ[aws_creds_prefix + 'AWS_ACCESS_KEY_ID']
    secret_access_key = os.environ[aws_creds_prefix + "AWS_SECRET_ACCESS_KEY"]

    assert access_key_id is not None
    assert secret_access_key is not None

    # Check that we cannot connect without setting credentials (this will only work if prefix is defined,
    # otherwise H2O will just pick it up from environment variables)
    if aws_creds_prefix:
        try:
            h2o.import_file("s3://test.0xdata.com/h2o-unit-tests/iris.csv")
            assert False
        except Exception as e:
            assert type(e) is h2o.exceptions.H2OServerError
            assert e.args[0].find("Error: Unable to load AWS credentials from any provider in the chain") != -1
            print("Validated that setting credentials using set_s3_credentials is necessary for import from S3 to work")

    # Now set the credentials
    # 1. Check PersistS3
    set_s3_credentials(access_key_id, secret_access_key)
    file = h2o.import_file("s3://test.0xdata.com/h2o-unit-tests/iris.csv")
    assert file is not None

    # 2. Check S3A (Using PersistHdfs)
    file = h2o.import_file("s3a://test.0xdata.com/h2o-unit-tests/iris.csv")
    assert file is not None

    access_key_id = 'abcd'
    secret_access_key = 'abcd'
    set_s3_credentials(access_key_id, secret_access_key)
    try:
        h2o.import_file("s3://test.0xdata.com/h2o-unit-tests/iris.csv")
        assert False
    except Exception as e:
        assert type(e) is h2o.exceptions.H2OServerError
        assert e.args[0].find("Error: The AWS Access Key Id you provided does not exist in our records. (Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId;") != -1


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_set_s3_credentials)
else:
    test_set_s3_credentials()
