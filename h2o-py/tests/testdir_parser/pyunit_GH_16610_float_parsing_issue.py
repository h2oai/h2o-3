import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def test_float_parsing_issue():
    badFloat1 = float.fromhex('0x1.1c6bbbd5ccfaep-362')
    badFloat2 = float.fromhex('0x1.0995342e882f9p-372')
    print(badFloat1, badFloat2)  # 1.1826684498151677e-109 1.0784540306167682e-112
    assert float(h2o.H2OFrame({'badFloat': [badFloat1]}).isna().sum()) == 0  # works
    assert float(h2o.H2OFrame({'badFloat': [badFloat2]}).isna().sum()) == 0  # works
    assert float(h2o.H2OFrame({'badFloat': [badFloat1, badFloat1]}).isna().sum()) == 0  # works
    assert float(h2o.H2OFrame({'badFloat': [badFloat2, badFloat2]}).isna().sum()) == 0  # works
    assert float(h2o.H2OFrame({'badFloat': [badFloat1, badFloat2]}).isna().sum()) == 0  # used to be broken, 2nd value => nan


pyunit_utils.standalone_test(test_float_parsing_issue)
