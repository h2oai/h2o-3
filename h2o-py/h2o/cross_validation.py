# -*- encoding: utf-8 -*-
from h2o.utils.compatibility import *  # NOQA

import numpy as np


class H2OPartitionIterator(object):
    def __init__(self, n):
        if abs(n - int(n)) >= 1e-15: raise ValueError("n must be an integer")
        self.n = int(n)
        self.masks = None
        self._fold_assignment_array = None

    def __iter__(self):
        """Yield ``(train_indices, test_indices)`` as 1-D numpy integer arrays.

        .. note::

            **Contract change since 3.46**: prior versions yielded ``(H2OFrame, H2OFrame)``
            boolean masks. The iterator now emits numpy integer index arrays so it
            interoperates with sklearn >= 1.6, whose ``_safe_indexing`` strict-converts
            cv splits via ``xp.asarray`` and refuses H2OFrame masks. Fold assignments
            are materialized to the driver ONCE (in ``_assign_folds``); for
            cluster-scale cross-validation prefer H2O's native ``nfolds=`` model
            parameter, which keeps everything server-side.
        """
        fold_arr = self._fold_assignment_numpy()
        all_idx = np.arange(self.n)
        for fold_index in range(len(self)):
            test_mask = fold_arr == fold_index
            yield all_idx[~test_mask], all_idx[test_mask]

    def _fold_assignment_numpy(self):
        """Materialize the fold-assignment column once and cache as a numpy int array."""
        raise NotImplementedError()

    def __len__(self):
        raise NotImplementedError()




class H2OKFold(H2OPartitionIterator):
    def __init__(self, fr, n_folds=3, seed=-1):
        H2OPartitionIterator.__init__(self, len(fr))
        self.n_folds = n_folds
        self.fr = fr
        self.seed = seed

    def __len__(self):
        return self.n_folds

    def _fold_assignment_numpy(self):
        if self._fold_assignment_array is None:
            if self.fr is None: raise ValueError("No H2OFrame available for computing folds.")
            fold_col = self.fr.kfold_column(self.n_folds, self.seed)
            self._fold_assignment_array = np.asarray(
                fold_col.as_data_frame()[fold_col.columns[0]].values, dtype=int
            )
            self.fr = None
        return self._fold_assignment_array


class H2OStratifiedKFold(H2OPartitionIterator):
    def __init__(self, y, n_folds=3, seed=-1):
        H2OPartitionIterator.__init__(self, len(y))
        self.n_folds = n_folds
        self.y = y
        self.seed = seed

    def __len__(self):
        return self.n_folds

    def _fold_assignment_numpy(self):
        if self._fold_assignment_array is None:
            if self.y is None: raise ValueError("No y available for computing stratified folds.")
            fold_col = self.y.stratified_kfold_column(self.n_folds, self.seed)
            self._fold_assignment_array = np.asarray(
                fold_col.as_data_frame()[fold_col.columns[0]].values, dtype=int
            )
            self.y = None
        return self._fold_assignment_array
