class Trees:

    def _ntrees_actual(self):
        """
        Returns actual number of trees in a tree model. If early stopping enabled, GBM can reset the ntrees value.
        In this case, the actual ntrees value is less than the original ntrees value a user set before
        building the model.
    
        Type: ``float``
        """
        return self.summary()['number_of_trees'][0]
