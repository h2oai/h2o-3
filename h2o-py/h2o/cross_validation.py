# -*- encoding: utf-8 -*-
from h2o.utils.compatibility import *  # NOQA

import warnings

# numpy/pandas are imported lazily inside the helpers below to stay consistent
# with the rest of h2o-py (which gates these imports via can_use_numpy() /
# can_use_pandas()). Eager imports here would break minimal installs even though
# h2o.cross_validation is opt-in.

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
        parameter (server-side, no driver round-trip) or call
        :meth:`iter_h2oframes` on this iterator to get the equivalent split as
        ``(train_h2oframe, test_h2oframe)`` without materializing fold indices
        on the driver. A one-shot :class:`FutureWarning` is emitted from the
        first materialization of fold indices so callers that relied on the old
        H2OFrame yield contract see a clear signal.
    """

    def __init__(self, n):
        if abs(n - int(n)) >= 1e-15: raise ValueError("n must be an integer")
        self.n = int(n)
        self._fold_assignment_array = None

    def __iter__(self):
        """Yield ``(train_indices, test_indices)`` as 1-D numpy integer arrays.

        See class docstring for the 3.46.0.10 contract change.
        """
        import numpy as np
        fold_arr = self._fold_assignment_numpy()
        all_idx = np.arange(self.n)
        for fold_index in range(len(self)):
            test_mask = fold_arr == fold_index
            yield all_idx[~test_mask], all_idx[test_mask]

    def iter_h2oframes(self):
        """Yield ``(train_h2oframe, test_h2oframe)`` for each fold, server-side.

        Unlike :meth:`__iter__` (which materializes integer indices to the
        Python driver to satisfy scikit-learn >= 1.6's strict-indexing contract),
        this method keeps the fold split inside the H2O cluster. Use it for
        cluster-scale cross-validation where the driver-materialized integer
        index lists would post multi-GB rapids payloads.

        Subclasses must implement :meth:`_fold_h2oframe` to expose the H2OFrame
        being split.
        """
        fold_col = self._fold_assignment_column()
        target = self._fold_h2oframe()
        col = fold_col.columns[0]
        for fold_index in range(len(self)):
            test = target[fold_col[col] == fold_index, :]
            train = target[fold_col[col] != fold_index, :]
            yield train, test

    def _fold_assignment_numpy(self):
        """Materialize the fold-assignment column once and cache as a numpy int array."""
        raise NotImplementedError()

    def _fold_assignment_column(self):
        """Return the (server-side) fold-assignment H2OFrame column.

        Used by :meth:`iter_h2oframes` to keep splits server-side. Default
        implementation drives off :meth:`_fold_assignment_numpy` and rebuilds
        the column on the cluster, which is wasteful — subclasses should
        override to expose the original fold column directly.
        """
        raise NotImplementedError()

    def _fold_h2oframe(self):
        """Return the H2OFrame being split. Subclasses must override."""
        raise NotImplementedError()

    def __len__(self):
        raise NotImplementedError()


_CONTRACT_CHANGE_MSG = (
    "H2OKFold/H2OStratifiedKFold yield contract changed in 3.46.0.10: iterators now "
    "emit (numpy int array, numpy int array) instead of (H2OFrame mask, H2OFrame mask). "
    "If you indexed back into the H2OFrame with the yielded masks, switch to integer "
    "indexing, use the new iter_h2oframes() method for server-side splits, or prefer "
    "H2O's native nfolds= model parameter for cluster-scale CV."
)


def _materialize_fold_column(fold_col):
    """Pull a single H2OFrame column to the driver as a compact numpy integer array.

    Guards against (a) NaN values in the fold column (would otherwise silently
    cast to a platform-dependent sentinel on numpy 2.x) and (b) wasted memory
    via the default int64 cast — fold IDs fit in int32.
    """
    import numpy as np
    import pandas
    col_name = fold_col.columns[0]
    # Pass na_values=[""] explicitly so the new as_data_frame NA-default warning
    # does not fire on every CV iteration. The fold column is integer-typed by
    # construction, so the literal "NA"/"NULL"/etc. string warning is noise.
    arr = fold_col.as_data_frame(na_values=[""])[col_name].values
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
            "rapids payloads per CV fold. Call iter_h2oframes() to keep the split "
            "server-side, or prefer H2O's native nfolds= model parameter for "
            "cluster-scale CV." % (n, approx_mb),
            category=UserWarning,
            stacklevel=3,
        )


class H2OKFold(H2OPartitionIterator):
    def __init__(self, fr, n_folds=3, seed=-1):
        H2OPartitionIterator.__init__(self, len(fr))
        self.n_folds = n_folds
        self.fr = fr
        self.seed = seed
        self._fold_column = None

    def __len__(self):
        return self.n_folds

    def _compute_fold_column(self):
        """Lazily compute the fold-assignment column on the H2O cluster (cached)."""
        if self._fold_column is None:
            if self.fr is None: raise ValueError("No H2OFrame available for computing folds.")
            self._fold_column = self.fr.kfold_column(self.n_folds, self.seed)
        return self._fold_column

    def _fold_assignment_numpy(self):
        if self._fold_assignment_array is None:
            # FutureWarning surfaces by default outside __main__, matching the
            # "Breaking change" status in Changes.md. Emitted at the moment users
            # actually pay the cost, not at constructor time.
            warnings.warn(_CONTRACT_CHANGE_MSG, category=FutureWarning, stacklevel=2)
            _maybe_warn_large_frame(self.n)
            self._fold_assignment_array = _materialize_fold_column(self._compute_fold_column())
        return self._fold_assignment_array

    def _fold_assignment_column(self):
        return self._compute_fold_column()

    def _fold_h2oframe(self):
        if self.fr is None:
            raise ValueError("No H2OFrame available; iter_h2oframes requires the source frame.")
        return self.fr


class H2OStratifiedKFold(H2OPartitionIterator):
    def __init__(self, y, n_folds=3, seed=-1):
        H2OPartitionIterator.__init__(self, len(y))
        self.n_folds = n_folds
        self.y = y
        self.seed = seed
        self._fold_column = None

    def __len__(self):
        return self.n_folds

    def _compute_fold_column(self):
        if self._fold_column is None:
            if self.y is None: raise ValueError("No y available for computing stratified folds.")
            self._fold_column = self.y.stratified_kfold_column(self.n_folds, self.seed)
        return self._fold_column

    def _fold_assignment_numpy(self):
        if self._fold_assignment_array is None:
            warnings.warn(_CONTRACT_CHANGE_MSG, category=FutureWarning, stacklevel=2)
            _maybe_warn_large_frame(self.n)
            self._fold_assignment_array = _materialize_fold_column(self._compute_fold_column())
        return self._fold_assignment_array

    def _fold_assignment_column(self):
        return self._compute_fold_column()

    def _fold_h2oframe(self):
        if self.y is None:
            raise ValueError("No y H2OFrame available; iter_h2oframes requires the source frame.")
        return self.y
