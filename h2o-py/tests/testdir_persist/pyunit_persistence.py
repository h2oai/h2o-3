import h2o
import os

from h2o.exceptions import H2OValueError
from h2o.persist import set_s3_credentials

from tests import pyunit_utils

def s3_access_test():

    access_key_id = os.environ['AWS_ACCESS_KEY_ID']
    secret_access_key = os.environ['AWS_SECRET_ACCESS_KEY']
    assert access_key_id is not None
    assert secret_access_key is not None
    set_s3_credentials(access_key_id, secret_access_key)
    
    file = h2o.import_file("s3://test.0xdata.com/h2o-unit-tests/iris.csv")
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

    try:
        set_s3_credentials("", "abcd")
        assert False
    except Exception as e:
        assert type(e) is H2OValueError
        assert e.args[0].find("Secret key ID must not be empty") != -1

    try:
        set_s3_credentials("abcd", "")
        assert False
    except Exception as e:
        assert type(e) is H2OValueError
        assert e.args[0].find("Secret access key must not be empty") != -1

    try:
        set_s3_credentials(None, "abcd")
        assert False
    except Exception as e:
        assert type(e) is H2OValueError
        assert e.args[0].find("Secret key ID must be specified") != -1

    try:
        set_s3_credentials("abcd", None)
        assert False
    except Exception as e:
        assert type(e) is H2OValueError
        assert e.args[0].find("Secret access key must be specified") != -1


if __name__ == "__main__":
    pyunit_utils.standalone_test(s3_access_test)
else:
    s3_access_test()
