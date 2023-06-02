import sys
import time
sys.path.insert(1,"../../")

from h2o.utils.shared_utils import is_module_available, can_use_pandas, can_use_numpy, LookupSeq
from tests import pyunit_utils as pu


def test_external_libraries_detection():
    assert can_use_pandas(), "pandas should be detected in test environment"
    assert can_use_numpy(), "numpy should be detected in test environment"
    assert is_module_available('matplotlib'), "matplotlib should be detected in test environment"
    assert is_module_available('sklearn'), "sklearn should be detected in test environment"
    assert not is_module_available('foobar'), "please don't"
    
    
def test_LookupSeq():
    l = list(range(1, 100))
    ll = LookupSeq(l)
    assert isinstance(ll, tuple)
    assert len(l) == len(ll) == len(ll.set())
    assert 57 in ll
    assert ll[57] == 58
    try:
        ll[42] = 24
        assert False, "should have failed"
    except TypeError as e:
        assert "'LookupSeq' object does not support item assignment" in str(e)
        
        
def test_LookupSeq_is_fast_for_lookups():
    cols = LookupSeq("C" + str(n) for n in range(1000000))
    assert len(cols) == 1000000
    diff = cols.set() - set("C"+str(n) for n in range(100000))
    assert len(diff) == 900000
    start = time.time()
    for c in diff:
        if c in cols:
            assert time.time() - start < 5, "too slow"  # reaching 0.4 on macOS, using generous upper limit for Jenkins
    duration = time.time() - start
    print(duration)
        

pu.run_tests([
    test_external_libraries_detection,
    test_LookupSeq,
    test_LookupSeq_is_fast_for_lookups,
])
