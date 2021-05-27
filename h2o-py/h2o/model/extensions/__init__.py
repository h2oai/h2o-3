"""
A collection of mixins optionally added to the model if the feature is supported for the given model.
"""

from .feature_interaction import FeatureInteraction
from .scoring_history import ScoringHistory, ScoringHistoryGLM
from .std_coef import StandardCoef
from .trees import Trees
from .varimp import VariableImportance

__all__ = ["FeatureInteraction", "ScoringHistory", "ScoringHistoryGLM", "StandardCoef", "Trees", "VariableImportance"]
