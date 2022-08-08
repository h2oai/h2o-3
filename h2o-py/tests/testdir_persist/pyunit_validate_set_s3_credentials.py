from h2o.exceptions import H2OValueError
from h2o.persist import set_s3_credentials

from tests import pyunit_utils


def test_validate_set_s3_credentials_inputs():
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
    pyunit_utils.standalone_test(test_validate_set_s3_credentials_inputs)
else:
    test_validate_set_s3_credentials_inputs()
