import sys

from h2o.utils.mixin import register_submodule

from .confusion_matrix import ConfusionMatrix
from .metrics_base import MetricsBase
from .metrics import *
from .model_base import ModelBase
from .models import *
from .segment_models import H2OSegmentModels

# order here impacts order of presentation in generated documentation
__all__ = ["ModelBase", "MetricsBase", 
           "H2OBinomialModel", "H2OMultinomialModel", "H2ORegressionModel", "H2OOrdinalModel",
           "H2OClusteringModel", "H2ODimReductionModel", "H2OAutoEncoderModel", "H2OBinomialUpliftModel",
           "ConfusionMatrix",  "H2OSegmentModels", ]


# Aliasing some submodules to 'h2o.model' for full backwards compatibility 
# after having moved some modules to `h2o.model.models` submodule.
# Note that users don't need to import those submodules in client 
# code except for some (old) top functions in `regression` submodule.
module = sys.modules[__name__]
for mod in ['regression']:
    register_submodule(module, name=mod, module=sys.modules["h2o.model.models.%s" % mod])

