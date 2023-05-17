import sys

sys.path.insert(1, "../../")
from h2o.backend import H2OConnectionConf
from tests import pyunit_utils


def conf_with_cacerts():
    conf = {
        'cacert': '/path/to/cacert'
    }
    connection_conf = H2OConnectionConf(config=conf)
    assert connection_conf.cacert == '/path/to/cacert'


if __name__ == "__main__":
    pyunit_utils.standalone_test(conf_with_cacerts)
else:
    conf_with_cacerts()
