from __future__ import print_function, unicode_literals
import sys
sys.path.insert(1,"../../")

from h2o.utils.compatibility import PList, PY2
from tests import pyunit_utils as pu


def test_list_to_str():
    l = ['foo', 'bar']
    assert repr(l) == str(l) == "[u'foo', u'bar']" if PY2 else "['foo', 'bar']"
    ll = list(l)
    assert repr(ll) == str(ll) == "[u'foo', u'bar']" if PY2 else "['foo', 'bar']"
    pl = PList(l)
    assert repr(pl) == str(pl) == "['foo', 'bar']"


pu.run_tests([
    test_list_to_str
])
