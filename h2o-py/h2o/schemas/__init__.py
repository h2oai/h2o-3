from functools import partial

from .error import H2OErrorV3, H2OModelBuilderErrorV3
from .metadata import H2OMetadataV3
from .schema import H2OSchema

__all__ = ['H2OSchema', 
           'H2OErrorV3', 'H2OModelBuilderErrorV3', 'H2OMetadataV3']


__schema_handlers = []


def get_schema_handler(schema):
    for s, h in __schema_handlers:
        if s == schema:
            return h
        elif callable(s) and s(schema):
            return partial(h, schema)


def register_schema_handler(schema, handler):
    """
    :param Union[str, Callable] schema: a string representing a schema name, or a predicate on a schema name.
    :param Callable handler: a function taking the schema payload as parameter, represented as a list or key-value tuples. 
        If the handler is a factory object with a `make` method, then that method will be used as the handler.
        if the schema is a predicate, then the handler function must accept 2 parameters: the schema name + the schema payload.
    """
    if hasattr(handler, "make") and callable(handler.make):
        handler = handler.make
    __schema_handlers.append((schema, handler))
    
    
def register_schemas():
    from h2o.backend import H2OCluster
    from h2o.model.metrics import make_metrics
    from h2o.two_dim_table import H2OTwoDimTable

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
    from h2o.backend import H2OCluster
    for cls in [H2OCluster, H2OErrorV3, H2OModelBuilderErrorV3]:
        cls.define_from_schema(conn)
