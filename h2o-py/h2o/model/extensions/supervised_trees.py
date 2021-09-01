from warnings import warn


class SupervisedTrees:

    def _update_tree_weights(self, frame, weights_column):
        """
        Re-calculates tree-node weights based on provided dataset. Modifying node weights will affect how
        contribution predictions (Shapley values) are calculated. This can be used to explain the model
        on a curated sub-population of the training dataset.

        :param frame: frame that will be used to re-populate trees with new observations and to collect per-node weights 
        :param weights_column: name of the weight column (can be different from training weights) 
        """
        from h2o.expr import ExprNode
        result = ExprNode("tree.update.weights", self, frame, weights_column)._eval_driver('scalar')._cache._data
        if result != "OK":
            warn(result)
