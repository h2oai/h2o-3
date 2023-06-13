from .tree import H2OTree
from .estimators import H2OGradientBoostingEstimator


def _equals_nan_friendly(l1, l2):
    for u, v in zip(l1, l2):
        if u is not v and u != v:
            return False
    return True


def equal_gbm_model_tree_structure(gbm_1, gbm_2):
    """
    Check if gbm models has the same tree structure. The purpose of this method is to debug GBM reproducibility.

    :param gbm_1: First model to check
    :param gbm_2: Second model to check
    :return: True if tree structure is equal
    """
    if (not isinstance(gbm_1, H2OGradientBoostingEstimator)) or not (isinstance(gbm_2, H2OGradientBoostingEstimator)):
        return False
    if gbm_1.ntrees != gbm_2.ntrees:
        return False
    if gbm_1._model_json["output"]["domains"] != gbm_2._model_json["output"]["domains"]:
        return False
    if gbm_1._model_json["output"]["model_category"] != gbm_2._model_json["output"]["model_category"]:
        return False
    if gbm_1._model_json["output"]["model_category"] == "Multinomial":
        tree_classes = gbm_1._model_json["output"]["domains"][-1]
    else:
        tree_classes = [None]
    for tree_id in range(gbm_1.ntrees):
        for output_class in tree_classes:
            tree1 = H2OTree(model=gbm_1, tree_number=tree_id, tree_class=output_class)
            tree2 = H2OTree(model=gbm_2, tree_number=tree_id, tree_class=output_class)
            if tree1.predictions != tree2.predictions:
                return False
            if tree1.decision_paths != tree2.decision_paths:
                return False
            if _equals_nan_friendly(tree1.thresholds, tree2.thresholds):
                return False
    return True

