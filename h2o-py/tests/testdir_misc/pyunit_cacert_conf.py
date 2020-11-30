import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.exceptions import H2OConnectionError

# Note: this test is relies on external service (badssl.com)
#       badssl.com provides a public endpoint with a self-signed certificate
def test_cacert_in_config():
    cfg = {
        "ip": "self-signed.badssl.com",
        "port": 443,
        "verify_ssl_certificates": True,
        "https": True
    }
    try:
        h2o.connect(config=cfg)
        assert False
    except H2OConnectionError as e:
        assert "CERTIFICATE_VERIFY_FAILED" in str(e)

    cfg["cacert"] = pyunit_utils.locate("smalldata/certs/badssl-cacert-2020.pem")
    try:
        h2o.connect(config=cfg)
        assert False
    except H2OConnectionError as e:
        # any response is a good response - TLS handshake was successful which proves the certificate was used 
        assert "HTTP 404 Not Found" in str(e)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_cacert_in_config)
else:
    test_cacert_in_config()
