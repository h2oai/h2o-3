import numpy as np
from itertools import combinations
from functools import partial

from sklearn.utils import column_or_1d,  check_consistent_length, check_array
from sklearn.preprocessing import label_binarize
from sklearn.preprocessing._label import _encode

from sklearn.metrics._base import _average_binary_score
from sklearn.metrics._ranking import _binary_roc_auc_score

from sklearn.utils.multiclass import type_of_target


def roc_auc_score(y_true, y_score, average="macro", sample_weight=None,
                  max_fpr=None, multi_class="raise", labels=None):
    """Compute Area Under the Receiver Operating Characteristic Curve (ROC AUC)
    from prediction scores.
    Note: this implementation can be used with binary, multiclass and
    multilabel classification, but some restrictions apply (see Parameters).
    Read more in the :ref:`User Guide <roc_metrics>`.
    Parameters
    ----------
    y_true : array-like of shape (n_samples,) or (n_samples, n_classes)
        True labels or binary label indicators. The binary and multiclass cases
        expect labels with shape (n_samples,) while the multilabel case expects
        binary label indicators with shape (n_samples, n_classes).
    y_score : array-like of shape (n_samples,) or (n_samples, n_classes)
        Target scores. In the binary and multilabel cases, these can be either
        probability estimates or non-thresholded decision values (as returned
        by `decision_function` on some classifiers). In the multiclass case,
        these must be probability estimates which sum to 1. The binary
        case expects a shape (n_samples,), and the scores must be the scores of
        the class with the greater label. The multiclass and multilabel
        cases expect a shape (n_samples, n_classes). In the multiclass case,
        the order of the class scores must correspond to the order of
        ``labels``, if provided, or else to the numerical or lexicographical
        order of the labels in ``y_true``.
    average : {'micro', 'macro', 'samples', 'weighted'} or None, \
            default='macro'
        If ``None``, the scores for each class are returned. Otherwise,
        this determines the type of averaging performed on the data:
        Note: multiclass ROC AUC currently only handles the 'macro' and
        'weighted' averages.
        ``'micro'``:
            Calculate metrics globally by considering each element of the label
            indicator matrix as a label.
        ``'macro'``:
            Calculate metrics for each label, and find their unweighted
            mean.  This does not take label imbalance into account.
        ``'weighted'``:
            Calculate metrics for each label, and find their average, weighted
            by support (the number of true instances for each label).
        ``'samples'``:
            Calculate metrics for each instance, and find their average.
        Will be ignored when ``y_true`` is binary.
    sample_weight : array-like of shape (n_samples,), default=None
        Sample weights.
    max_fpr : float > 0 and <= 1, default=None
        If not ``None``, the standardized partial AUC [2]_ over the range
        [0, max_fpr] is returned. For the multiclass case, ``max_fpr``,
        should be either equal to ``None`` or ``1.0`` as AUC ROC partial
        computation currently is not supported for multiclass.
    multi_class : {'raise', 'ovr', 'ovo'}, default='raise'
        Multiclass only. Determines the type of configuration to use. The
        default value raises an error, so either ``'ovr'`` or ``'ovo'`` must be
        passed explicitly.
        ``'ovr'``:
            Computes the AUC of each class against the rest [3]_ [4]_. This
            treats the multiclass case in the same way as the multilabel case.
            Sensitive to class imbalance even when ``average == 'macro'``,
            because class imbalance affects the composition of each of the
            'rest' groupings.
        ``'ovo'``:
            Computes the average AUC of all possible pairwise combinations of
            classes [5]_. Insensitive to class imbalance when
            ``average == 'macro'``.
    labels : array-like of shape (n_classes,), default=None
        Multiclass only. List of labels that index the classes in ``y_score``.
        If ``None``, the numerical or lexicographical order of the labels in
        ``y_true`` is used.
    Returns
    -------
    auc : float
    References
    ----------
    .. [1] `Wikipedia entry for the Receiver operating characteristic
            <https://en.wikipedia.org/wiki/Receiver_operating_characteristic>`_
    .. [2] `Analyzing a portion of the ROC curve. McClish, 1989
            <https://www.ncbi.nlm.nih.gov/pubmed/2668680>`_
    .. [3] Provost, F., Domingos, P. (2000). Well-trained PETs: Improving
           probability estimation trees (Section 6.2), CeDER Working Paper
           #IS-00-04, Stern School of Business, New York University.
    .. [4] `Fawcett, T. (2006). An introduction to ROC analysis. Pattern
            Recognition Letters, 27(8), 861-874.
            <https://www.sciencedirect.com/science/article/pii/S016786550500303X>`_
    .. [5] `Hand, D.J., Till, R.J. (2001). A Simple Generalisation of the Area
            Under the ROC Curve for Multiple Class Classification Problems.
            Machine Learning, 45(2), 171-186.
            <http://link.springer.com/article/10.1023/A:1010920819831>`_
    See also
    --------
    average_precision_score : Area under the precision-recall curve
    roc_curve : Compute Receiver operating characteristic (ROC) curve
    Examples
    --------
    >>> import numpy as np
    >>> from sklearn.metrics import roc_auc_score
    >>> y_true = np.array([0, 0, 1, 1])
    >>> y_scores = np.array([0.1, 0.4, 0.35, 0.8])
    >>> roc_auc_score(y_true, y_scores)
    0.75
    """

    y_type = type_of_target(y_true)
    y_true = check_array(y_true, ensure_2d=False, dtype=None)
    y_score = check_array(y_score, ensure_2d=False)

    if y_type == "multiclass" or (y_type == "binary" and
                                  y_score.ndim == 2 and
                                  y_score.shape[1] > 2):
        # do not support partial ROC computation for multiclass
        if max_fpr is not None and max_fpr != 1.:
            raise ValueError("Partial AUC computation not available in "
                             "multiclass setting, 'max_fpr' must be"
                             " set to `None`, received `max_fpr={0}` "
                             "instead".format(max_fpr))
        if multi_class == 'raise':
            raise ValueError("multi_class must be in ('ovo', 'ovr')")
        return _multiclass_roc_auc_score(y_true, y_score, labels,
                                         multi_class, average, sample_weight)
    elif y_type == "binary":
        labels = np.unique(y_true)
        y_true = label_binarize(y_true, classes=labels)[:, 0]
        return _average_binary_score(partial(_binary_roc_auc_score,
                                             max_fpr=max_fpr),
                                     y_true, y_score, average,
                                     sample_weight=sample_weight)
    else:  # multilabel-indicator
        return _average_binary_score(partial(_binary_roc_auc_score,
                                             max_fpr=max_fpr),
                                     y_true, y_score, average,
                                     sample_weight=sample_weight)


def _average_multiclass_ovo_score(binary_metric, y_true, y_score,
                                  average='macro'):
    """Average one-versus-one scores for multiclass classification.
    Uses the binary metric for one-vs-one multiclass classification,
    where the score is computed according to the Hand & Till (2001) algorithm.
    Parameters
    ----------
    binary_metric : callable
        The binary metric function to use that accepts the following as input:
            y_true_target : array, shape = [n_samples_target]
                Some sub-array of y_true for a pair of classes designated
                positive and negative in the one-vs-one scheme.
            y_score_target : array, shape = [n_samples_target]
                Scores corresponding to the probability estimates
                of a sample belonging to the designated positive class label
    y_true : array-like of shape (n_samples,)
        True multiclass labels.
    y_score : array-like of shape (n_samples, n_classes)
        Target scores corresponding to probability estimates of a sample
        belonging to a particular class.
    average : {'macro', 'weighted'}, default='macro'
        Determines the type of averaging performed on the pairwise binary
        metric scores:
        ``'macro'``:
            Calculate metrics for each label, and find their unweighted
            mean. This does not take label imbalance into account. Classes
            are assumed to be uniformly distributed.
        ``'weighted'``:
            Calculate metrics for each label, taking into account the
            prevalence of the classes.
    Returns
    -------
    score : float
        Average of the pairwise binary metric scores.
    """
    check_consistent_length(y_true, y_score)

    y_true_unique = np.unique(y_true)
    n_classes = y_true_unique.shape[0]
    n_pairs = n_classes * (n_classes - 1) // 2
    pair_scores = np.empty(n_pairs)

    is_weighted = average == "weighted"
    prevalence = np.empty(n_pairs) if is_weighted else None

    # Compute scores treating a as positive class and b as negative class,
    # then b as positive class and a as negative class
    for ix, (a, b) in enumerate(combinations(y_true_unique, 2)):
        a_mask = y_true == a
        b_mask = y_true == b
        ab_mask = np.logical_or(a_mask, b_mask)

        if is_weighted:
            prevalence[ix] = np.average(ab_mask)

        a_true = a_mask[ab_mask]
        b_true = b_mask[ab_mask]

        a_true_score = binary_metric(a_true, y_score[ab_mask, a])
        b_true_score = binary_metric(b_true, y_score[ab_mask, b])
        pair_scores[ix] = (a_true_score + b_true_score) / 2

    return np.average(pair_scores, weights=prevalence)


def _multiclass_roc_auc_score(y_true, y_score, labels,
                              multi_class, average, sample_weight):
    """Multiclass roc auc score
    Parameters
    ----------
    y_true : array-like of shape (n_samples,)
        True multiclass labels.
    y_score : array-like of shape (n_samples, n_classes)
        Target scores corresponding to probability estimates of a sample
        belonging to a particular class
    labels : array, shape = [n_classes] or None, optional (default=None)
        List of labels to index ``y_score`` used for multiclass. If ``None``,
        the lexical order of ``y_true`` is used to index ``y_score``.
    multi_class : string, 'ovr' or 'ovo'
        Determines the type of multiclass configuration to use.
        ``'ovr'``:
            Calculate metrics for the multiclass case using the one-vs-rest
            approach.
        ``'ovo'``:
            Calculate metrics for the multiclass case using the one-vs-one
            approach.
    average : 'macro' or 'weighted', optional (default='macro')
        Determines the type of averaging performed on the pairwise binary
        metric scores
        ``'macro'``:
            Calculate metrics for each label, and find their unweighted
            mean. This does not take label imbalance into account. Classes
            are assumed to be uniformly distributed.
        ``'weighted'``:
            Calculate metrics for each label, taking into account the
            prevalence of the classes.
    sample_weight : array-like of shape (n_samples,), default=None
        Sample weights.
    """
    # validation of the input y_score
    if not np.allclose(1, y_score.sum(axis=1)):
        raise ValueError(
            "Target scores need to be probabilities for multiclass "
            "roc_auc, i.e. they should sum up to 1.0 over classes")

    # validation for multiclass parameter specifications
    average_options = ("macro", "weighted")
    if average not in average_options:
        raise ValueError("average must be one of {0} for "
                         "multiclass problems".format(average_options))

    multiclass_options = ("ovo", "ovr")
    if multi_class not in multiclass_options:
        raise ValueError("multi_class='{0}' is not supported "
                         "for multiclass ROC AUC, multi_class must be "
                         "in {1}".format(
            multi_class, multiclass_options))

    if labels is not None:
        labels = column_or_1d(labels)
        classes = _encode(labels)
        if len(classes) != len(labels):
            raise ValueError("Parameter 'labels' must be unique")
        if not np.array_equal(classes, labels):
            raise ValueError("Parameter 'labels' must be ordered")
        if len(classes) != y_score.shape[1]:
            raise ValueError(
                "Number of given labels, {0}, not equal to the number "
                "of columns in 'y_score', {1}".format(
                    len(classes), y_score.shape[1]))
        if len(np.setdiff1d(y_true, classes)):
            raise ValueError(
                "'y_true' contains labels not in parameter 'labels'")
    else:
        classes = _encode(y_true)
        if len(classes) != y_score.shape[1]:
            raise ValueError(
                "Number of classes in y_true not equal to the number of "
                "columns in 'y_score'")

    if multi_class == "ovo":
        if sample_weight is not None:
            raise ValueError("sample_weight is not supported "
                             "for multiclass one-vs-one ROC AUC, "
                             "'sample_weight' must be None in this case.")
        _, y_true_encoded = _encode(y_true, uniques=classes, encode=True)
        # Hand & Till (2001) implementation (ovo)
        return _average_multiclass_ovo_score(_binary_roc_auc_score,
                                             y_true_encoded,
                                             y_score, average=average)
    else:
        # ovr is same as multi-label
        y_true_multilabel = label_binarize(y_true, classes=classes)
        return _average_binary_score(_binary_roc_auc_score, y_true_multilabel,
                                     y_score, average,
                                     sample_weight=sample_weight)
