# -*- encoding: utf-8 -*-
from h2o.utils.compatibility import *  # NOQA

import warnings

# numpy/pandas are imported lazily inside the helpers below to stay consistent
# with the rest of h2o-py (which gates these imports via can_use_numpy() /
# can_use_pandas()). Eager imports here would break minimal installs even though
# h2o.cross_validation is opt-in.

# Above this many rows, emit a per-process UserWarning so users know they are
# paying driver-side materialization + rapids POST cost (the integer index list
# is serialized inline into the rapids AST). Threshold matches the order of
# magnitude where a 5-fold split starts pushing > 100MB rapids payloads.
_LARGE_FRAME_THRESHOLD = 1_000_000

# Process-wide latch for the contract-change FutureWarning. Without this, a
# GridSearchCV that constructs N iterators emits the same warning N times.
_CONTRACT_WARN_FIRED = False

# Process-wide latch for the large-frame UserWarning (same rationale).
_LARGE_FRAME_WARN_FIRED = False


class H2OPartitionIterator(object):
    """Base class for cross-validation iterators that emit ``(train_idx, test_idx)``
    as 1-D numpy integer arrays.

    .. versionchanged:: 3.46.0.11

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
        H2OFrame yield contract see a clear signal. The legacy mask-yielding
        iteration is preserved for one release as :meth:`iter_legacy` with a
        :class:`DeprecationWarning`.
    """

    def __init__(self, n):
        if abs(n - int(n)) >= 1e-15: raise ValueError("n must be an integer")
        self.n = int(n)
        self._fold_assignment_array = None

    def __iter__(self):
        """Yield ``(train_indices, test_indices)`` as 1-D numpy integer arrays.

        See class docstring for the 3.46.0.11 contract change.
        """
        import numpy as np
        fold_arr = self._fold_assignment_numpy()
        all_idx = np.arange(self.n)
        for fold_index in range(len(self)):
            test_mask = fold_arr == fold_index
            yield all_idx[~test_mask], all_idx[test_mask]

    def iter_legacy(self):
        """Yield the pre-3.46.0.11 ``(train_mask_frame, test_mask_frame)`` contract.

        Emits a :class:`DeprecationWarning` on the first call. Provided as a
        one-release transition shim for callers that indexed back into the
        source H2OFrame with the yielded masks. Will be removed in a future
        release; migrate to :meth:`__iter__` (integer indices) or
        :meth:`iter_h2oframes` (server-side frame splits).
        """
        warnings.warn(
            "H2OKFold.iter_legacy() is a transition shim and will be removed in a future release. "
            "Use the default iteration (yields numpy int arrays) or iter_h2oframes() instead.",
            category=DeprecationWarning,
            stacklevel=2,
        )
        fold_col = self._fold_assignment_column()
        target = self._fold_h2oframe()
        col = fold_col.columns[0]
        for fold_index in range(len(self)):
            test_mask = fold_col[col] == fold_index
            train_mask = fold_col[col] != fold_index
            yield train_mask, test_mask

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
        """Materialize the fold-assignment column once and cache as a numpy int array.

        Default template-method implementation: defers to subclass-provided
        :meth:`_compute_fold_column` for the H2OFrame fold column, then
        materializes it once. Subclasses normally only need to override
        :meth:`_compute_fold_column` and :meth:`_fold_h2oframe`.
        """
        global _CONTRACT_WARN_FIRED
        if self._fold_assignment_array is None:
            if not _CONTRACT_WARN_FIRED:
                warnings.warn(_CONTRACT_CHANGE_MSG, category=FutureWarning, stacklevel=2)
                _CONTRACT_WARN_FIRED = True
            _maybe_warn_large_frame(self.n, n_folds=len(self))
            self._fold_assignment_array = _materialize_fold_column(self._compute_fold_column())
        return self._fold_assignment_array

    def _fold_assignment_column(self):
        """Return the (server-side) fold-assignment H2OFrame column.

        Used by :meth:`iter_h2oframes` and :meth:`iter_legacy` to keep splits
        server-side. Default implementation defers to the subclass-provided
        :meth:`_compute_fold_column`.
        """
        return self._compute_fold_column()

    def _compute_fold_column(self):
        """Compute (and cache) the fold-assignment H2OFrame column. Subclasses must override."""
        raise NotImplementedError()

    def _fold_h2oframe(self):
        """Return the H2OFrame being split. Subclasses must override."""
        raise NotImplementedError()

    def __len__(self):
        raise NotImplementedError()


_CONTRACT_CHANGE_MSG = (
    "H2OKFold/H2OStratifiedKFold yield contract changed in 3.46.0.11: iterators now "
    "emit (numpy int array, numpy int array) instead of (H2OFrame mask, H2OFrame mask). "
    "If you indexed back into the H2OFrame with the yielded masks, switch to integer "
    "indexing, use the new iter_h2oframes() method for server-side splits, or call "
    "iter_legacy() for a one-release transition shim that yields the old mask contract."
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


def _maybe_warn_large_frame(n, n_folds=5):
    """Warn once per process when materializing a large fold column.

    The integer index list is posted once to rapids (not per fold), so the
    payload estimate uses (n_folds-1)/n_folds × n × 10 bytes — the largest
    train slice across all folds. Reported as max-per-iteration to keep the
    user-visible number conservative.
    """
    global _LARGE_FRAME_WARN_FIRED
    if n > _LARGE_FRAME_THRESHOLD and not _LARGE_FRAME_WARN_FIRED:
        # Estimate max rapids payload for a single (train, test) yield:
        #   ~10 bytes per int × (n_folds - 1)/n_folds × n bytes
        approx_mb = (n_folds - 1) * n * 10 / n_folds / (1024 * 1024)
        warnings.warn(
            "H2OKFold materializing %d-row fold column to driver; expect up to ~%.0fMB "
            "rapids payload per yielded train slice. Call iter_h2oframes() to keep the "
            "split server-side, or prefer H2O's native nfolds= model parameter for "
            "cluster-scale CV." % (n, approx_mb),
            category=UserWarning,
            stacklevel=3,
        )
        _LARGE_FRAME_WARN_FIRED = True


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

    def _fold_h2oframe(self):
        if self.fr is None:
            raise ValueError("No H2OFrame available; iter_h2oframes requires the source frame.")
        return self.fr

    @property
    def fold_assignments(self):
        """Pre-3.46.0.11 attribute: the fold-assignment H2OFrame column.

        Preserved for one release as a property alias of :meth:`_fold_assignment_column`.
        New code should call :meth:`__iter__` (yields numpy int arrays) or
        :meth:`iter_h2oframes` (yields server-side frame splits).
        """
        warnings.warn(
            "H2OKFold.fold_assignments is preserved for one release. "
            "Use _fold_assignment_column(), iter_h2oframes(), or H2O's native nfolds= parameter instead.",
            category=DeprecationWarning,
            stacklevel=2,
        )
        return self._compute_fold_column()


class H2OStratifiedKFold(H2OPartitionIterator):
    """Stratified K-fold iterator.

    .. versionchanged:: 3.46.0.11

        Constructor now accepts an optional ``fr`` keyword: the feature frame
        to split. If provided, :meth:`iter_h2oframes` yields ``(train_fr,
        test_fr)`` slices of ``fr``. If not provided, :meth:`iter_h2oframes`
        raises :class:`NotImplementedError` (the legacy behavior — yielding
        slices of the response column alone — was silently broken for the
        documented sklearn-compat migration path).
    """
    def __init__(self, y, n_folds=3, seed=-1, fr=None):
        H2OPartitionIterator.__init__(self, len(y))
        self.n_folds = n_folds
        self.y = y
        self.fr = fr
        self.seed = seed
        self._fold_column = None

    def __len__(self):
        return self.n_folds

    def _compute_fold_column(self):
        if self._fold_column is None:
            if self.y is None: raise ValueError("No y available for computing stratified folds.")
            self._fold_column = self.y.stratified_kfold_column(self.n_folds, self.seed)
        return self._fold_column

    def _fold_h2oframe(self):
        if self.fr is None:
            raise NotImplementedError(
                "H2OStratifiedKFold.iter_h2oframes() requires the feature frame. "
                "Construct as H2OStratifiedKFold(y, n_folds=N, fr=feature_frame) to enable it. "
                "For driver-side integer-index iteration (sklearn-compatible), this is not needed."
            )
        return self.fr

    @property
    def fold_assignments(self):
        """Pre-3.46.0.11 attribute: the fold-assignment H2OFrame column. See :class:`H2OKFold.fold_assignments`."""
        warnings.warn(
            "H2OStratifiedKFold.fold_assignments is preserved for one release. "
            "Use _fold_assignment_column(), iter_h2oframes(), or H2O's native nfolds= parameter instead.",
            category=DeprecationWarning,
            stacklevel=2,
        )
        return self._compute_fold_column()
