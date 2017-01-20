# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA


class H2OPartitionIterator(object):
    def __init__(self, n):
        if abs(n - int(n)) >= 1e-15: raise ValueError("n must be an integer")
        self.n = int(n)
        self.masks = None

    def __iter__(self):
        for test_mask in self._test_masks():
            yield 1 - test_mask, test_mask

    def _test_masks(self):
        raise NotImplementedError()




class H2OKFold(H2OPartitionIterator):
    def __init__(self, fr, n_folds=3, seed=-1):
        H2OPartitionIterator.__init__(self, len(fr))
        self.n_folds = n_folds
        self.fr = fr
        self.seed = seed
        self.fold_assignments = None

    def __len__(self):
        return self.n_folds

    def _test_masks(self):
        if self.fold_assignments is None:
            self._assign_folds()
        if self.masks is None: self.masks = [i == self.fold_assignments for i in range(self.n_folds)]
        return self.masks

    def _assign_folds(self):
        if self.fr is None: raise ValueError("No H2OFrame available for computing folds.")
        self.fold_assignments = self.fr.kfold_column(self.n_folds, self.seed)
        self.fr = None




class H2OStratifiedKFold(H2OPartitionIterator):
    def __init__(self, y, n_folds=3, seed=-1):
        H2OPartitionIterator.__init__(self, len(y))
        self.n_folds = n_folds
        self.y = y
        self.seed = seed
        self.fold_assignments = None

    def __len__(self):
        return self.n_folds

    def _test_masks(self):
        if self.fold_assignments is None:
            self._assign_folds()
        if self.masks is None: self.masks = [i == self.fold_assignments for i in range(self.n_folds)]
        return self.masks

    def _assign_folds(self):
        if self.y is None: raise ValueError("No y available for computing stratified folds.")
        self.fold_assignments = self.y.stratified_kfold_column(self.n_folds, self.seed)
        self.y = None
