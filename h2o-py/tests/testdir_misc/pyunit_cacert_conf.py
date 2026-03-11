import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.exceptions import H2OConnectionError
import os

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

    path = pyunit_utils.locate("results")
    cert_file = os.path.join(path, "badssl-cacert.pem")

    # Download self-signed certificate
    os.system("openssl s_client -showcerts -verify 0 -connect self-signed.badssl.com:443 -servername self-signed.badssl.com < /dev/null > {0}".format(cert_file))
    cfg["cacert"] = cert_file
    try:
        h2o.connect(config=cfg)
        assert False
    except H2OConnectionError as e:
        # any response is a good response - TLS handshake was successful which proves the certificate was used 
        strErr = str(e)
        assert "HTTP 404 Not Found" in strErr or "X509: NO_CERTIFICATE_OR_CRL_FOUND" in strErr or \
            "[X509] no certificate or crl found" in strErr


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_cacert_in_config)
else:
    test_cacert_in_config()
