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


class H2OPartitionIterator(object):
    """Base class for H2O cross-validation iterators.

    Iterating the object directly (``for train, test in kf``) yields the
    historical ``(train_mask_frame, test_mask_frame)`` H2OFrame boolean masks.

    For scikit-learn use the standard splitter interface :meth:`split` /
    :meth:`get_n_splits`, which yields ``(train_idx, test_idx)`` as 1-D numpy
    integer arrays: scikit-learn >= 1.6's ``_safe_indexing`` strict-converts CV
    splits via ``xp.asarray`` and refuses H2OFrame masks, so the integer-index
    path is required there. :meth:`split` materializes fold assignments to the
    Python driver once per iterator instance; for cluster-scale cross-validation
    prefer H2O's native ``nfolds=`` model parameter (server-side, no driver
    round-trip) or :meth:`iter_h2oframes`, which yields ``(train_fr, test_fr)``
    slices without materializing fold indices on the driver.

    .. versionchanged:: 3.46.0.12

        Added the scikit-learn splitter interface (:meth:`split`,
        :meth:`get_n_splits`) and :meth:`iter_h2oframes` so the iterator
        interoperates with scikit-learn >= 1.6 *without* changing the historical
        ``__iter__`` H2OFrame-mask contract.
    """

    def __init__(self, n):
        if abs(n - int(n)) >= 1e-15: raise ValueError("n must be an integer")
        self.n = int(n)
        self.masks = None
        self._fold_assignment_array = None

    def __iter__(self):
        """Yield ``(train_mask_frame, test_mask_frame)`` H2OFrame boolean masks.

        This is the historical contract (slice the source H2OFrame with the
        masks). For scikit-learn integer-index splits use :meth:`split`.
        """
        for test_mask in self._test_masks():
            yield 1 - test_mask, test_mask

    def split(self, X=None, y=None, groups=None):
        """scikit-learn splitter API: yield ``(train_idx, test_idx)`` as 1-D numpy
        integer arrays.

        ``X``/``y``/``groups`` are accepted for scikit-learn signature
        compatibility and ignored — fold assignment is computed server-side from
        the frame/response bound at construction. Fold assignments are
        materialized to the driver once per iterator instance; for cluster-scale
        cross-validation prefer H2O's native ``nfolds=`` or :meth:`iter_h2oframes`.
        """
        import numpy as np
        fold_arr = self._fold_assignment_numpy()
        all_idx = np.arange(self.n)
        for fold_index in range(self.get_n_splits()):
            test_mask = fold_arr == fold_index
            yield all_idx[~test_mask], all_idx[test_mask]

    def get_n_splits(self, X=None, y=None, groups=None):
        """scikit-learn splitter API: the number of folds. Arguments are ignored."""
        return len(self)

    def iter_h2oframes(self):
        """Yield ``(train_h2oframe, test_h2oframe)`` for each fold, server-side.

        Unlike :meth:`split` (which materializes integer indices to the Python
        driver to satisfy scikit-learn >= 1.6's strict-indexing contract), this
        method keeps the fold split inside the H2O cluster. Use it for
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

    def _test_masks(self):
        """Compute (and cache) the per-fold test masks as H2OFrame boolean columns."""
        if self.masks is None:
            fold_col = self._compute_fold_column()
            col = fold_col.columns[0]
            self.masks = [fold_col[col] == i for i in range(len(self))]
        return self.masks

    def _fold_assignment_numpy(self):
        """Materialize the fold-assignment column once and cache as a numpy int array.

        Default template-method implementation: defers to subclass-provided
        :meth:`_compute_fold_column` for the H2OFrame fold column, then
        materializes it once. Subclasses normally only need to override
        :meth:`_compute_fold_column` and :meth:`_fold_h2oframe`.
        """
        if self._fold_assignment_array is None:
            _maybe_warn_large_frame(self.n, n_folds=len(self))
            self._fold_assignment_array = _materialize_fold_column(self._compute_fold_column())
        return self._fold_assignment_array

    def _fold_assignment_column(self):
        """Return the (server-side) fold-assignment H2OFrame column.

        Used by :meth:`iter_h2oframes`. Default implementation defers to the
        subclass-provided :meth:`_compute_fold_column`.
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


def _materialize_fold_column(fold_col):
    """Pull a single H2OFrame column to the driver as a compact numpy integer array.

    Guards against (a) NaN values in the fold column (would otherwise silently
    cast to a platform-dependent sentinel on numpy 2.x) and (b) wasted memory
    via the default int64 cast — fold IDs fit in int32.
    """
    import numpy as np
    import pandas
    col_name = fold_col.columns[0]
    # Pass na_values=[""] explicitly so the as_data_frame NA-default warning does
    # not fire on every CV iteration. The fold column is integer-typed by
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
    if n > _LARGE_FRAME_THRESHOLD:
        # Estimate max rapids payload for a single (train, test) yield:
        #   ~10 bytes per int × (n_folds - 1)/n_folds × n bytes
        # The warnings module dedups identical messages per process (default filter).
        approx_mb = (n_folds - 1) * n * 10 / n_folds / (1024 * 1024)
        warnings.warn(
            "H2OKFold materializing %d-row fold column to driver; expect up to ~%.0fMB "
            "rapids payload per yielded train slice. Call iter_h2oframes() to keep the "
            "split server-side, or prefer H2O's native nfolds= model parameter for "
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

    def _fold_h2oframe(self):
        if self.fr is None:
            raise ValueError("No H2OFrame available; iter_h2oframes requires the source frame.")
        return self.fr

    @property
    def fold_assignments(self):
        """The fold-assignment H2OFrame column (computed lazily on the cluster)."""
        return self._compute_fold_column()


class H2OStratifiedKFold(H2OPartitionIterator):
    """Stratified K-fold iterator.

    .. versionchanged:: 3.46.0.12

        Constructor now accepts an optional ``fr`` keyword: the feature frame
        to split. If provided, :meth:`iter_h2oframes` yields ``(train_fr,
        test_fr)`` slices of ``fr``. If not provided, :meth:`iter_h2oframes`
        raises :class:`NotImplementedError` (the legacy behavior — yielding
        slices of the response column alone — was silently broken for the
        documented sklearn-compat migration path).
    """
    def __init__(self, y, n_folds=3, seed=-1, fr=None):
        H2OPartitionIterator.__init__(self, len(y))
        # The fold column is built from y but the integer indices / server-side
        # splits are applied to fr (the feature frame). They must share row count
        # and order, otherwise iter_h2oframes()/split() silently misalign folds.
        if fr is not None and len(fr) != len(y):
            raise ValueError(
                "H2OStratifiedKFold: fr (%d rows) and y (%d rows) must have the same "
                "number of rows." % (len(fr), len(y)))
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
        """The fold-assignment H2OFrame column (computed lazily on the cluster)."""
        return self._compute_fold_column()
