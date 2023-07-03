import sys
sys.path.insert(1,"../../")

from h2o.utils.compatibility import PList
from tests import pyunit_utils as pu


def test_list_to_str():
    l = ['foo', 'bar', 1, True, str("baz"), u"bal"]
    print(repr(l))
    print(str(l))
    assert repr(l) == str(l) == "['foo', 'bar', 1, True, 'baz', 'bal']"
    ll = list(l)
    assert repr(ll) == str(ll) == "['foo', 'bar', 1, True, 'baz', 'bal']"
    pl = PList(l)
    assert repr(pl) == str(pl) == "['foo', 'bar', 1, True, 'baz', 'bal']"


pu.run_tests([
    test_list_to_str
])
