import sys

sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils as pu


# Deliberate canary for accidental changes to the CloudV3 schema: bump this when a field is
# added to or removed from water.api.schemas3.CloudV3. Fields that are only populated in
# specific deployments belong in OPTIONAL_PROPERTIES rather than being asserted non-null.
EXPECTED_PROPERTY_COUNT = 27

OPTIONAL_PROPERTIES = (
    "web_ip",          # only set when the cluster binds a specific web interface
    "hadoop_version",  # only set when the cluster was launched via h2odriver
)


def test_cluster_status():
    h2o.cluster().show_status(True)


def test_cluster_properties():
    cl = h2o.cluster()
    assert len(cl._schema_attrs_) == EXPECTED_PROPERTY_COUNT, \
        "CloudV3 exposes %d properties, expected %d - update EXPECTED_PROPERTY_COUNT if the " \
        "schema changed on purpose" % (len(cl._schema_attrs_), EXPECTED_PROPERTY_COUNT)
    for k in cl._schema_attrs_.keys():
        assert getattr(cl, k) is not None or k in OPTIONAL_PROPERTIES, \
            "cluster property `%s` is None" % k


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

