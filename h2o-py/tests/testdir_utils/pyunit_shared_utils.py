from __future__ import print_function
import sys
sys.path.insert(1,"../../")

from h2o.utils.shared_utils import is_module_available, can_use_pandas, can_use_numpy
from tests import pyunit_utils as pu


def test_external_libraries_detection():
    assert can_use_pandas(), "pandas should be detected in test environment"
    assert can_use_numpy(), "numpy should be detected in test environment"
    assert is_module_available('matplotlib'), "matplotlib should be detected in test environment"
    assert is_module_available('sklearn'), "sklearn should be detected in test environment"
    assert not is_module_available('foobar'), "please don't"
    

pu.run_tests([
    test_external_libraries_detection
])
