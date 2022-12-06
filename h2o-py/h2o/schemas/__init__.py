from .error import H2OErrorV3, H2OModelBuilderErrorV3
from .metadata import H2OMetadataV3
from .schema import H2OSchema

__all__ = ['H2OSchema', 
           'H2OErrorV3', 'H2OModelBuilderErrorV3', 'H2OMetadataV3']


__schema_handlers = []


def get_schema_handler(schema):
    for s, h in __schema_handlers:
        if s == schema or (callable(s) and s(schema)):
            return h


def register_schema_handler(schema, handler):
    if hasattr(handler, "make") and callable(handler.make):
        handler = handler.make
    __schema_handlers.append(schema, handler)
    
    
def register_schemas():
    from ..backend import H2OCluster
    from ..model.metrics import make_metrics
    from ..two_dim_table import H2OTwoDimTable
    
    for (schema, handler) in [
        ("MetadataV3", H2OMetadataV3),
        ("CloudV3", H2OCluster),
        ("H2OErrorV3", H2OErrorV3),
        ("H2OModelBuilderErrorV3", H2OModelBuilderErrorV3),
        ("TwoDimTableV3", H2OTwoDimTable),
        (lambda s: s.startswith("ModelMetrics"), make_metrics),
    ]:
        register_schema_handler(schema, handler)


def define_classes_from_schema(conn):
    from ..backend import H2OCluster
    for cls in [H2OCluster, H2OErrorV3, H2OModelBuilderErrorV3]:
        cls.define_from_schema(conn)
