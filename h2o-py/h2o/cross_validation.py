# -*- encoding: utf-8 -*-
from h2o.utils.compatibility import *  # NOQA

import warnings

import numpy as np
import pandas

from h2o.exceptions import H2ODeprecationWarning


# H2ODeprecationWarning extends DeprecationWarning, which CPython's default filter
# suppresses for code outside __main__. The 3.46.0.10 contract change is a hard
# break for any caller that consumed the previous H2OFrame yield contract, so we
# force these warnings to surface at least once per process.
warnings.filterwarnings("default", category=H2ODeprecationWarning, module=__name__)

# Above this many rows, emit a per-iterator UserWarning so users know they are
# paying driver-side materialization + rapids POST cost for each fold (the
# integer index lists are serialized inline into the rapids AST). Threshold
# matches the order of magnitude where a 5-fold split starts pushing > 100MB
# rapids payloads.
_LARGE_FRAME_THRESHOLD = 1_000_000


class H2OPartitionIterator(object):
    """Base class for cross-validation iterators that emit ``(train_idx, test_idx)``
    as 1-D numpy integer arrays.

    .. versionchanged:: 3.46.0.10

        Yield contract changed from ``(H2OFrame mask, H2OFrame mask)`` to
        ``(numpy int array, numpy int array)`` so iterators interoperate with
        scikit-learn >= 1.6, whose ``_safe_indexing`` strict-converts CV splits
        via ``xp.asarray`` and refuses H2OFrame masks. Fold assignments are
        materialized to the Python driver ONCE per iterator instance; for
        cluster-scale cross-validation prefer H2O's native ``nfolds=`` model
        parameter, which keeps everything server-side. A one-shot
        :class:`H2ODeprecationWarning` is emitted from the first materialization
        of fold indices so callers that relied on the old H2OFrame yield contract
        see a clear signal.
    """

    def __init__(self, n):
        if abs(n - int(n)) >= 1e-15: raise ValueError("n must be an integer")
        self.n = int(n)
        self._fold_assignment_array = None

    def __iter__(self):
        """Yield ``(train_indices, test_indices)`` as 1-D numpy integer arrays.

        See class docstring for the 3.46.0.10 contract change.
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


_CONTRACT_CHANGE_MSG = (
    "H2OKFold/H2OStratifiedKFold yield contract changed in 3.46.0.10: iterators now "
    "emit (numpy int array, numpy int array) instead of (H2OFrame mask, H2OFrame mask). "
    "If you indexed back into the H2OFrame with the yielded masks, switch to integer "
    "indexing or prefer H2O's native nfolds= model parameter for cluster-scale CV."
)


def _materialize_fold_column(fold_col):
    """Pull a single H2OFrame column to the driver as a compact numpy integer array.

    Guards against (a) NaN values in the fold column (would otherwise silently
    cast to a platform-dependent sentinel on numpy 2.x) and (b) wasted memory
    via the default int64 cast — fold IDs fit in int32.
    """
    col_name = fold_col.columns[0]
    arr = fold_col.as_data_frame()[col_name].values
    if pandas.isna(arr).any():
        raise RuntimeError(
            "kfold_column produced NA fold assignments — cannot build CV splits"
        )
    return arr.astype(np.int32, copy=False)


def _maybe_warn_large_frame(n):
    if n > _LARGE_FRAME_THRESHOLD:
        # Rough payload estimate: each fold posts a list of ints decimal-encoded
        # to the rapids endpoint; ~10 bytes per int × (K-1)/K × n × K folds.
        # We don't know K here, so report the per-fold cost conservatively.
        approx_mb = n * 10 / (1024 * 1024)
        warnings.warn(
            "H2OKFold materializing %d-row fold column to driver; expect ~%.0fMB "
            "rapids payloads per CV fold. For cluster-scale CV prefer H2O's native "
            "nfolds= model parameter, which keeps splits server-side." % (n, approx_mb),
            category=UserWarning,
            stacklevel=3,
        )


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
            # Surface the contract change at the moment users actually pay the cost,
            # not at constructor time (probing the iterator without iterating is a
            # legitimate idiom). H2ODeprecationWarning is force-defaulted at module
            # import so this is visible even in non-__main__ scripts.
            warnings.warn(_CONTRACT_CHANGE_MSG, category=H2ODeprecationWarning, stacklevel=2)
            _maybe_warn_large_frame(self.n)
            fold_col = self.fr.kfold_column(self.n_folds, self.seed)
            self._fold_assignment_array = _materialize_fold_column(fold_col)
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
            warnings.warn(_CONTRACT_CHANGE_MSG, category=H2ODeprecationWarning, stacklevel=2)
            _maybe_warn_large_frame(self.n)
            fold_col = self.y.stratified_kfold_column(self.n_folds, self.seed)
            self._fold_assignment_array = _materialize_fold_column(fold_col)
            self.y = None
        return self._fold_assignment_array
