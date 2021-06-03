"""
A collection of mixins optionally added to the model if the feature is supported for the given model.
"""

from .feature_interaction import FeatureInteraction
from .scoring_history import ScoringHistory, ScoringHistoryDL, ScoringHistoryGLM, ScoringHistoryTrees
from .std_coef import StandardCoef
from .trees import Trees
from .varimp import VariableImportance
