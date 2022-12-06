supervised_learning = None   # actually depends on the estimator model in the pipeline, leave it to None for now as it is needed only for training and we don't support pipeline as input yet


# in future update, we'll want to expose parameters applied to each transformer
def module_extensions():
    class DataTransformer(H2ODisplay):
        @classmethod
        def make(cls, kvs):
            dt = DataTransformer(**{k: v for k, v in kvs if k not in H2OSchema._ignored_schema_keys_})
            dt._json = kvs
            return dt
        
        def __init__(self, id=None):
            self._json = None
            self.id = id
            
        def _repr_(self):
            return repr(self._json)
        
        def _str_(self, verbosity=None):
            return repr_def(self)
        
        
    # self-register transformer class: done as soon as `h2o.estimators` is loaded, which means as soon as h2o.h2o is...
    register_schema_handler("DataTransformerV3", DataTransformer)
    
    
def class_extensions():
    @property
    def transformers(self):
        return self._model_json['output']['transformers']
        
    @property
    def estimator_model(self):
        m_json = self._model_json['output']['estimator']
        return None if (m_json is None or m_json['name'] is None) else h2o.get_model(m_json['name'])


extensions = dict(
    __imports__="""
import h2o
from h2o.display import H2ODisplay, repr_def
from h2o.schemas import H2OSchema, register_schema_handler
""",
    __class__=class_extensions,
    __module__=module_extensions,
)
