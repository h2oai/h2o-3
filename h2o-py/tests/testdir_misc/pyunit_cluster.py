import sys

sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils as pu


def test_cluster_status():
    h2o.cluster().show_status(True)


def test_cluster_properties():
    cl = h2o.cluster()
    assert len(cl._schema_attrs_) == 25
    for k in cl._schema_attrs_.keys():
        assert getattr(cl, k) is not None or k == "web_ip"
    

def test_exception_on_unknown_cluster_property():
    cl = h2o.cluster()
    try:
        assert cl.unknown_prop is not None, "should have failed before the assertion"
    except AttributeError as e:
        assert "Unknown attribute `unknown_prop` on object of type `H2OCluster`, this property is not available for this H2O backend" in str(e)


pu.run_tests([
    test_cluster_status,
    test_cluster_properties,
    test_exception_on_unknown_cluster_property
])

