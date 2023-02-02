"""
A collection of mixins optionally added to the model if the feature is supported for the given model.
.. note::
    Classes in this module are used at runtime as mixins: their methods can (and should) be accessed directly from a trained model.
"""
import sys

from .fairness import Fairness
from .feature_interaction import FeatureInteraction
from .h_statistic import HStatistic
from .scoring_history import ScoringHistory, ScoringHistoryDL, ScoringHistoryGLM, ScoringHistoryTrees
from .std_coef import StandardCoef
from .trees import Trees
from .supervised_trees import SupervisedTrees
from .varimp import VariableImportance
from .contributions import Contributions
from .row_to_tree_assignment import RowToTreeAssignment

module = sys.modules[__name__]


def has_extension(model, ext):  # type: (ModelBase, str) -> bool 
    """
    Any short class name visible in this module is considered as a valid model extension.
    For example, the extension mixin class `h2o.model.extensions.VariableImportance` will be checked using
    
    .. code-block:: python
    
        has_extension(model, 'VariableImportance')
        
    Also, when there are multiple implementations of the same extension, it is recommended to test only for the base class.
    For example, to test if the model supports scoring history, simply use:
    
    .. code-block:: python
    
        has_extension(model, 'ScoringHistory')
        
    :param model: the model to check.
    :param ext: the name of the extension that may be available or not on the model.
    :return: True iff the model supports the extension.
    """
    ext_cls = getattr(module, ext, None)
    if ext_cls is None:
        raise ValueError("Unknown model extension `%s`: see module `%s` for the full list of supported extensions." % (ext, __name__))
    return isinstance(model, ext_cls)


__all__ = [  # mainly useful here for the generated documentation
    'FeatureInteraction', 
    'HStatistic', 
    'ScoringHistory', 
    'StandardCoef', 
    'Trees',
    'SupervisedTrees', 
    'VariableImportance',
    'Contributions',
    'Fairness',
    'RowToTreeAssignment'
]
