# -*- encoding: utf-8 -*-
import os
import random
import warnings
from collections import OrderedDict, Counter, defaultdict
from contextlib import contextmanager

try:
    from StringIO import StringIO  # py2 (first as py2 also has io.StringIO, but only with unicode support)
except:
    from io import StringIO  # py3

import h2o
import numpy as np
from h2o.exceptions import H2OValueError
from h2o.plot import decorate_plot_result, get_matplotlib_pyplot, is_decorated_plot_result


def _display(object):
    """
    Display the object.
    :param object: An object to be displayed.
    :returns: the input
    """
    import matplotlib.figure
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
    if (isinstance(object, matplotlib.figure.Figure) and matplotlib.get_backend().lower() != "agg") or is_decorated_plot_result(object):
        plt.show()
    else:
        try:
            import IPython.display
            IPython.display.display(object)
        except ImportError:
            print(object)
    if isinstance(object, matplotlib.figure.Figure):
        plt.close(object)
        print("\n")
    if is_decorated_plot_result(object) and (object.figure() is not None):
        plt.close(object.figure())
        print("\n")
    return object


def _dont_display(object):
    """
    Don't display the object
    :param object: that should not be displayed
    :returns: input
    """
    import matplotlib.figure
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
    if isinstance(object, matplotlib.figure.Figure):
        plt.close()
    return object


# UTILS
class Header:
    """
    Class representing a Header with pretty printing for IPython.
    """

    def __init__(self, content, level=1):
        self.content = content
        self.level = level

    def _repr_html_(self):
        return "<h{level}>{content}</h{level}>".format(level=self.level, content=self.content)

    def _repr_markdown_(self):
        return "\n\n{} {}".format("#" * self.level, self.content)

    def _repr_pretty_(self, p, cycle):
        p.text(str(self))

    def __str__(self):
        return self._repr_markdown_()


class Description:
    """
    Class representing a Description with pretty printing for IPython.
    """
    DESCRIPTIONS = dict(
        leaderboard="Leaderboard shows models with their metrics. When provided with H2OAutoML object, "
                    "the leaderboard shows 5-fold cross-validated metrics by default (depending on the "
                    "H2OAutoML settings), otherwise it shows metrics computed on the frame. "
                    "At most 20 models are shown by default.",
        leaderboard_row="Leaderboard shows models with their metrics and their predictions for a given row. "
                        "When provided with H2OAutoML object, the leaderboard shows 5-fold cross-validated "
                        "metrics by default (depending on the H2OAutoML settings), otherwise it shows "
                        "metrics computed on the frame. At most 20 models are shown by default.",
        confusion_matrix="Confusion matrix shows a predicted class vs an actual class.",
        residual_analysis="Residual Analysis plots the fitted values vs residuals on a test dataset. Ideally, "
                          "residuals should be randomly distributed. Patterns in this plot can indicate potential "
                          "problems with the model selection, e.g., using simpler model than necessary, not accounting "
                          "for heteroscedasticity, autocorrelation, etc. Note that if you see \"striped\" lines of "
                          "residuals, that is an artifact of having an integer valued (vs a real valued) "
                          "response variable.",
        learning_curve="Learning curve plot shows the loss function/metric dependent on number of iterations or trees "
                       "for tree-based algorithms. This plot can be useful for determining whether the model overfits.",
        variable_importance="The variable importance plot shows the relative importance of the most "
                            "important variables in the model.",
        varimp_heatmap="Variable importance heatmap shows variable importance across multiple models. "
                       "Some models in H2O return variable importance for one-hot (binary indicator) "
                       "encoded versions of categorical columns (e.g. Deep Learning, XGBoost). "
                       "In order for the variable importance of categorical columns to be compared "
                       "across all model types we compute a summarization of the the variable importance "
                       "across all one-hot encoded features and return a single variable importance for the "
                       "original categorical feature. By default, the models and variables are ordered by "
                       "their similarity.",
        model_correlation_heatmap="This plot shows the correlation between the predictions of the models. "
                                  "For classification, frequency of identical predictions is used. By default, "
                                  "models are ordered by their similarity (as computed by hierarchical clustering). "
                                  "Interpretable models, such as GAM, GLM, and RuleFit are highlighted using "
                                  "red colored text.",
        shap_summary="SHAP summary plot shows the contribution of the features for each instance (row of data). "
                     "The sum of the feature contributions and the bias term is equal to the raw prediction of "
                     "the model, i.e., prediction before applying inverse link function.",
        pdp="Partial dependence plot (PDP) gives a graphical depiction of the marginal effect of a variable on "
            "the response. The effect of a variable is measured in change in the mean response. PDP assumes "
            "independence between the feature for which is the PDP computed and the rest.",
        ice="An Individual Conditional Expectation (ICE) plot gives a graphical depiction of the marginal effect "
            "of a variable on the response. ICE plots are similar to partial dependence plots (PDP); PDP shows the "
            "average effect of a feature while ICE plot shows the effect for a single instance. This function will "
            "plot the effect for each decile. In contrast to the PDP, ICE plots can provide more insight, especially "
            "when there is stronger feature interaction.",
        ice_row="Individual conditional expectations (ICE) plot gives a graphical depiction of the marginal "
                "effect of a variable on the response for a given row. ICE plot is similar to partial "
                "dependence plot (PDP), PDP shows the average effect of a feature while ICE plot shows "
                "the effect for a single instance.",
        shap_explain_row="SHAP explanation shows contribution of features for a given instance. The sum "
                         "of the feature contributions and the bias term is equal to the raw prediction of "
                         "the model, i.e., prediction before applying inverse link function. H2O implements "
                         "TreeSHAP which when the features are correlated, can increase contribution of a feature "
                         "that had no influence on the prediction.",
        fairness_metrics="The following table shows fairness metrics for intersections determined using "
                         "the protected_columns. Apart from the fairness metrics, there is a p-value "
                         "from Fisher's exact test or G-test (depends on the size of the intersections) "
                         "for hypothesis that being selected (positive response) is independent to "
                         "being in the reference group or a particular protected group.\n\n"
                         "After the table there are two kinds of plot. The first kind starts with AIR prefix "
                         "which stands for Adverse Impact Ratio. These plots show values relative to the "
                         "reference group and also show two dashed lines corresponding to 0.8 and 1.25 "
                         "(the four-fifths rule). \n The second kind is showing the absolute value of given "
                         "metrics. The reference group is shown by using a different colored bar.",
        fairness_roc="The following plot shows a Receiver Operating Characteristic (ROC) for each "
                     "intersection. This plot could be used for selecting different threshold of the "
                     "classifier to make it more fair in some sense this is described in, e.g., "
                     "HARDT, Moritz, PRICE, Eric and SREBRO, Nathan, 2016. Equality of Opportunity in "
                     "Supervised Learning. arXiv:1610.02413.",
        fairness_prc="The following plot shows a Precision-Recall Curve for each intersection.",
        fairness_varimp="Permutation variable importance is obtained by measuring the distance between "
                        "prediction errors before and after a feature is permuted; only one feature at "
                        "a time is permuted.",
        fairness_pdp="The following plots show partial dependence for each intersection separately. "
                     "This plot can be used to see how the membership to a particular intersection "
                     "influences the dependence on a given feature.",
        fairness_shap="The following plots show SHAP contributions for individual intersections and "
                      "one feature at a time. "
                      "This plot can be used to see how the membership to a particular intersection "
                      "influences the dependence on a given feature.",
    )

    def __init__(self, for_what):
        self.content = self.DESCRIPTIONS[for_what]

    def _repr_html_(self):
        return "<blockquote>{}</blockquote>".format(self.content)

    def _repr_markdown_(self):
        return "\n> {}".format(self.content)

    def _repr_pretty_(self, p, cycle):
        p.text(str(self))

    def __str__(self):
        return self._repr_markdown_()


class H2OExplanation(OrderedDict):
    def _ipython_display_(self):
        from IPython.display import display
        for v in self.values():
            display(v)


@contextmanager
def no_progress_block():
    """
    A context manager that temporarily blocks showing the H2O's progress bar.
    Used when a multiple models are evaluated.
    """
    progress = h2o.job.H2OJob.__PROGRESS_BAR__
    if progress:
        h2o.no_progress()
    try:
        yield
    finally:
        if progress:
            h2o.show_progress()


class NumpyFrame:
    """
    Simple class that very vaguely emulates Pandas DataFrame.
    Main purpose is to keep parsing from the List of Lists format to numpy.
    This class is meant to be used just in the explain module.
    Due to that fact it encodes the factor variables similarly to R/pandas -
    factors are mapped to numeric column which in turn makes it easier to plot it.
    """

    def __init__(self, h2o_frame):
        # type: ("NumpyFrame", Union[h2o.H2OFrame, h2o.two_dim_table.H2OTwoDimTable]) -> None
        if isinstance(h2o_frame, h2o.two_dim_table.H2OTwoDimTable):
            self._columns = h2o_frame.col_header
            _is_numeric = np.array([type_ in ["double", "float", "long", "integer"]
                                    for type_ in h2o_frame.col_types], dtype=bool)
            _is_factor = np.array([type_ in ["string"] for type_ in h2o_frame.col_types],
                                  dtype=bool)
            df = h2o_frame.cell_values
            self._factors = dict()
            for col in range(len(self._columns)):
                if _is_factor[col]:
                    levels = set(row[col] for row in df)
                    self._factors[self._columns[col]] = list(levels)

            self._data = np.empty((len(df), len(self._columns)), dtype=np.float64)
            df = [self._columns] + df
        elif isinstance(h2o_frame, h2o.H2OFrame):
            _is_factor = np.array(h2o_frame.isfactor(), dtype=bool) | np.array(
                h2o_frame.ischaracter(), dtype=bool)
            _is_numeric = h2o_frame.isnumeric()
            self._columns = h2o_frame.columns
            self._factors = {col: h2o_frame[col].asfactor().levels()[0] for col in
                             np.array(h2o_frame.columns)[_is_factor]}

            df = h2o_frame.as_data_frame(False)
            self._data = np.empty((h2o_frame.nrow, h2o_frame.ncol))
        else:
            raise RuntimeError("Unexpected type of \"h2o_frame\": {}".format(type(h2o_frame)))

        for idx, col in enumerate(df[0]):
            if _is_factor[idx]:
                convertor = self.from_factor_to_num(col)
                self._data[:, idx] = np.array(
                    [float(convertor.get(
                        row[idx] if not (len(row) == 0 or row[idx] == "") else "nan", "nan"))
                        for row in df[1:]], dtype=np.float64)
            elif _is_numeric[idx]:
                self._data[:, idx] = np.array(
                    [float(row[idx] if not (len(row) == 0 or row[idx] == "") else "nan") for row in
                     df[1:]],
                    dtype=np.float64)
            else:
                try:
                    self._data[:, idx] = np.array([row[idx]
                                                   if not (
                            len(row) == 0 or
                            row[idx] == "" or
                            row[idx].lower() == "nan"
                    ) else "nan" for row in df[1:]], dtype=np.float64)
                    if h2o_frame.type(self._columns[idx]) == "time":
                        self._data[:, idx] = _timestamp_to_mpl_datetime(self._data[:, idx])
                except Exception:
                    raise RuntimeError("Unexpected type of column {}!".format(col))

    def isfactor(self, column):
        # type: ("NumpyFrame", str) -> bool
        """
        Is column a factor/categorical column?

        :param column: string containing the column name
        
        :returns: boolean
        """
        return column in self._factors or self._get_column_and_factor(column)[0] in self._factors

    def from_factor_to_num(self, column):
        # type: ("NumpyFrame", str) -> Dict[str, int]
        """
        Get a dictionary mapping a factor to its numerical representation in the NumpyFrame

        :param column: string containing the column name
        
        :returns: dictionary
        """
        fact = self._factors[column]
        return dict(zip(fact, range(len(fact))))

    def from_num_to_factor(self, column):
        # type: ("NumpyFrame", str) -> Dict[int, str]
        """
        Get a dictionary mapping numerical representation of a factor to the category names.

        :param column: string containing the column name
        
        :returns: dictionary
        """
        fact = self._factors[column]
        return dict(zip(range(len(fact)), fact))

    def _get_column_and_factor(self, column):
        # type: ("NumpyFrame", str) -> Tuple[str, Optional[float]]
        """
        Get a column name and possibly a factor name.
        This is used to get proper column name and factor name when provided
        with the output of some algos such as XGBoost which encode factor
        columns to "column_name.category_name".

        :param column: string containing the column name
        :returns: tuple (column_name: str, factor_name: Optional[str])
        """
        if column in self.columns:
            return column, None
        if column.endswith(".") and column[:-1] in self.columns:
            return column[:-1], None

        col_parts = column.split(".")
        for i in range(1, len(col_parts) + 1):
            if ".".join(col_parts[:i]) in self.columns:
                column = ".".join(col_parts[:i])
                factor_name = ".".join(col_parts[i:])
                if factor_name == "missing(NA)":
                    factor = float("nan")
                else:
                    factor = self.from_factor_to_num(column)[factor_name]
                return column, factor

    def __getitem__(self, indexer):
        # type: ("NumpyFrame", Union[str, Tuple[Union[int,List[int]], str]]) -> np.ndarray
        """
        A low level way to get a column or a row within a column.
        NOTE: Returns numeric representation even for factors.

        :param indexer: string for the whole column or a tuple (row_index, column_name)
        :returns: a column or a row within a column
        """
        row = slice(None)
        if isinstance(indexer, tuple):
            row = indexer[0]
            column = indexer[1]
        else:
            column = indexer

        if column not in self.columns:
            column, factor = self._get_column_and_factor(column)
            if factor is not None:
                if factor != factor:
                    return np.asarray(np.isnan(self._data[row, self.columns.index(column)]),
                                      dtype=np.float32)
                return np.asarray(self._data[row, self.columns.index(column)] == factor,
                                  dtype=np.float32)
        return self._data[row, self.columns.index(column)]

    def __setitem__(self, key, value):
        # type: ("NumpyFrame", str, np.ndarray) -> None
        """
        Rudimentary implementation of setitem. Setting a factor column is not supported.
        Use with caution.
        :param key: column name
        :param value: ndarray representing one whole column
        """
        if key not in self.columns:
            raise KeyError("Column {} is not present amongst {}".format(key, self.columns))
        if self.isfactor(key):
            raise NotImplementedError("Setting a factor column is not supported!")
        self._data[:, self.columns.index(key)] = value

    def get(self, column, as_factor=True):
        # type: ("NumpyFrame", str, bool) -> np.ndarray
        """
        Get a column.

        :param column: string containing the column name
        :param as_factor: if True (default), factor column will contain string
                          representation; otherwise numerical representation
        :returns: A column represented as numpy ndarray
        """
        if as_factor and self.isfactor(column):
            column, factor_idx = self._get_column_and_factor(column)
            if factor_idx is not None:
                return self[column] == factor_idx
            convertor = self.from_num_to_factor(column)
            return np.array([convertor.get(row, "") for row in self[column]])
        return self[column]

    def levels(self, column):
        # type: ("NumpyFrame", str) -> List[str]
        """
        Get levels/categories of a factor column.

        :param column: a string containing the column name
        :returns: list of levels
        """
        return self._factors.get(column, [])

    def nlevels(self, column):
        # type: ("NumpyFrame", str) -> int
        """
        Get number of levels/categories of a factor column.

        :param column: string containing the column name
        :returns: a number of levels within a factor
        """
        return len(self.levels(column))

    @property
    def columns(self):
        # type: ("NumpyFrame") -> List[str]
        """
        Column within the NumpyFrame.

        :returns: list of columns
        """
        return self._columns

    @property
    def nrow(self):
        # type: ("NumpyFrame") -> int
        """
        Number of rows.

        :returns: number of rows
        """
        return self._data.shape[0]

    @property
    def ncol(self):
        # type: ("NumpyFrame") -> int
        """
        Number of columns.

        :returns: number of columns
        """
        return self._data.shape[1]

    @property
    def shape(self):
        # type: ("NumpyFrame") -> Tuple[int, int]
        """
        Shape of the frame.

        :returns: tuple (number of rows, number of columns)
        """
        return self._data.shape

    def sum(self, axis=0):
        # type: ("NumpyFrame", int) -> np.ndarray
        """
        Calculate the sum of the NumpyFrame elements over the given axis.

        WARNING: This method doesn't care if the column is categorical or numeric. Use with care.

        :param axis: Axis along which a sum is performed.
        :returns: numpy.ndarray with shape same as NumpyFrame with the `axis` removed
        """
        return self._data.sum(axis=axis)

    def mean(self, axis=0):
        # type: ("NumpyFrame", int) -> np.ndarray
        """
        Calculate the mean of the NumpyFrame elements over the given axis.

        WARNING: This method doesn't care if the column is categorical or numeric. Use with care.

        :param axis: Axis along which a mean is performed.
        :returns: numpy.ndarray with shape same as NumpyFrame with the `axis` removed
        """
        return self._data.mean(axis=axis)

    def items(self, with_categorical_names=False):
        # type: ("NumpyFrame", bool) -> Generator[Tuple[str, np.ndarray], None, None]
        """
        Make a generator that yield column name and ndarray with values.

        :params with_categorical_names: if True, factor columns are returned as string columns;
                                        otherwise numerical
        :returns: generator to be iterated upon
        """
        for col in self.columns:
            yield col, self.get(col, with_categorical_names)


def _mpl_datetime_to_str(mpl_datetime):
    # type: (float) -> str
    """
    Convert matplotlib-compatible date time which in which the unit is a day to a human-readable string.

    :params mpl_datetime: number of days since the beginning of the unix epoch
    :returns: string containing date time
    """
    from datetime import datetime
    # convert to seconds and then to datetime
    return datetime.utcfromtimestamp(mpl_datetime * 3600 * 24).strftime('%Y-%m-%d %H:%M:%S')


def _timestamp_to_mpl_datetime(timestamp):
    """
   Convert timestamp to matplotlib compatible timestamp.
   :params timestamp: number of ms since the beginning of the unix epoch
   :returns: number of days since the beginning of the unix epoch
    """
    return timestamp / (1000 * 3600 * 24)


def _get_domain_mapping(model):
    """
    Get a mapping between columns and their domains.

    :return: Dictionary containing a mapping column -> factors
    """
    output = model._model_json["output"]
    return dict(zip(output["names"], output["domains"]))


def _shorten_model_ids(model_ids):
    import re
    regexp = re.compile(r"(.*)_AutoML_[\d_]+((?:_.*)?)$")  # nested group needed for Py2
    shortened_model_ids = [regexp.sub(r"\1\2", model_id) for model_id in model_ids]
    if len(set(shortened_model_ids)) == len(set(model_ids)):
        return shortened_model_ids
    return model_ids


def _get_algorithm(model,  treat_xrt_as_algorithm=False):
    # type: (Union[str, h2o.model.ModelBase], bool) -> str
    """
    Get algorithm type. Use model id to infer it if possible.
    :param model: model or a model_id
    :param treat_xrt_as_algorithm: boolean used for best_of_family
    :returns: string containing algorithm name
    """
    if not isinstance(model, h2o.model.ModelBase):
        import re
        algo = re.search("^(DeepLearning|DRF|GAM|GBM|GLM|NaiveBayes|StackedEnsemble|RuleFit|XGBoost|XRT)(?=_)", model)
        if algo is not None:
            algo = algo.group(0).lower()
            if algo == "xrt" and not treat_xrt_as_algorithm:
                algo = "drf"
            return algo
        else:
            model = h2o.get_model(model)
    if treat_xrt_as_algorithm and model.algo == "drf":
        if model.actual_params.get("histogram_type") == "Random":
            return "xrt"
    return model.algo


def _first_of_family(models, all_stackedensembles=False):
    # type: (Union[str, h2o.model.ModelBase], bool) -> Union[str, h2o.model.ModelBase]
    """
    Get first of family models
    :param models: models or model ids
    :param all_stackedensembles: if True return all stacked ensembles
    :returns: list of models or model ids (the same type as on input)
    """
    selected_models = []
    included_families = set()
    for model in models:
        family = _get_algorithm(model, treat_xrt_as_algorithm=True)
        if family not in included_families or (all_stackedensembles and "stackedensemble" == family):
            selected_models.append(model)
            included_families.add(family)
    return selected_models


def _density(xs, bins=100):
    # type: (np.ndarray, int) -> np.ndarray
    """
    Make an approximate density estimation by blurring a histogram (used for SHAP summary plot).
    :param xs: numpy vector
    :param bins: number of bins
    :returns: density values
    """
    if len(xs) < 10:
        return np.zeros(len(xs))
    hist = list(np.histogram(xs, bins=bins))
    # gaussian blur
    hist[0] = np.convolve(hist[0],
                          [0.00598, 0.060626, 0.241843,
                           0.383103,
                           0.241843, 0.060626, 0.00598])[3:-3]
    hist[0] = hist[0] / np.max(hist[0])
    hist[1] = (hist[1][:-1] + hist[1][1:]) / 2
    return np.interp(xs, hist[1], hist[0])


def _uniformize(data, col_name):
    # type: (NumpyFrame, str) -> np.ndarray
    """
    Convert to quantiles.
    :param data: NumpyFrame
    :param col_name: string containing a column name
    :returns: quantile values of individual points in the column
    """
    if col_name not in data.columns or data.isfactor(col_name):
        res = data[col_name]
        diff = (np.nanmax(res) - np.nanmin(res))
        if diff <= 0 or np.isnan(diff):
            return res
        res = (res - np.nanmin(res)) / diff
        return res

    col = data[col_name]
    xs = np.linspace(0, 1, 100)
    quantiles = np.nanquantile(col, xs)
    res = np.interp(col, quantiles, xs)
    res = (res - np.nanmin(res)) / (np.nanmax(res) - np.nanmin(res))
    return res


# PLOTS
def shap_summary_plot(
        model,  # type: h2o.model.ModelBase
        frame,  # type: h2o.H2OFrame
        columns=None,  # type: Optional[Union[List[int], List[str]]]
        top_n_features=20,  # type: int
        samples=1000,  # type: int
        colorize_factors=True,  # type: bool
        alpha=1,  # type: float
        colormap=None,  # type: str
        figsize=(12, 12),  # type: Union[Tuple[float], List[float]]
        jitter=0.35,  # type: float
        save_plot_path=None # type: Optional[str]
):  # type: (...) -> plt.Figure
    """
    SHAP summary plot.

    The SHAP summary plot shows the contribution of features for each instance. The sum
    of the feature contributions and the bias term is equal to the raw prediction
    of the model (i.e. prediction before applying inverse link function).

    :param model: h2o tree model (e.g. DRF, XRT, GBM, XGBoost).
    :param frame: H2OFrame.
    :param columns: either a list of columns or column indices to show. If specified
                    parameter ``top_n_features`` will be ignored.
    :param top_n_features: a number of columns to pick using variable importance (where applicable).
    :param samples: maximum number of observations to use; if lower than number of rows in the
                    frame, take a random sample.
    :param colorize_factors: if ``True``, use colors from the colormap to colorize the factors;
                             otherwise all levels will have same color.
    :param alpha: transparency of the points.
    :param colormap: colormap to use instead of the default blue to red colormap.
    :param figsize: figure size; passed directly to matplotlib.
    :param jitter: amount of jitter used to show the point density.
    :param save_plot_path: a path to save the plot via using matplotlib function savefig.
    :returns: object that contains the resulting matplotlib figure (can be accessed using ``result.figure()``).

    :examples:
    
    >>> import h2o
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train a GBM
    >>> gbm = H2OGradientBoostingEstimator()
    >>> gbm.train(y=response, training_frame=train)
    >>>
    >>> # Create SHAP summary plot
    >>> gbm.shap_summary_plot(test)
    """
    import matplotlib.colors
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
    blue_to_red = matplotlib.colors.LinearSegmentedColormap.from_list("blue_to_red",
                                                                      ["#00AAEE", "#FF1166"])

    if colormap is None:
        colormap = blue_to_red
    else:
        colormap = plt.get_cmap(colormap)

    if top_n_features < 0:
        top_n_features = float("inf")

    # to prevent problems with data sorted in some logical way
    # (overplotting with latest result which might have different values
    # then the rest of the data in a given region)
    permutation = list(range(frame.nrow))
    random.shuffle(permutation)
    if samples is not None:
        permutation = sorted(permutation[:min(len(permutation), samples)])
        frame = frame[permutation, :]
        permutation = list(range(frame.nrow))
        random.shuffle(permutation)

    with no_progress_block():
        contributions = NumpyFrame(model.predict_contributions(frame))
    frame = NumpyFrame(frame)
    contribution_names = contributions.columns

    feature_importance = sorted(
        {k: np.abs(v).mean() for k, v in contributions.items() if "BiasTerm" != k}.items(),
        key=lambda kv: kv[1])
    if columns is None:
        top_n = min(top_n_features, len(feature_importance))
        top_n_features = [fi[0] for fi in feature_importance[-top_n:]]
    else:
        picked_cols = []
        columns = [frame.columns[col] if isinstance(col, int) else col for col in columns]
        for feature in columns:
            if feature in contribution_names:
                picked_cols.append(feature)
            else:
                for contrib in contribution_names:
                    if contrib.startswith(feature + "."):
                        picked_cols.append(contrib)
        top_n_features = picked_cols

    plt.figure(figsize=figsize)
    plt.grid(True)
    plt.axvline(0, c="black")

    for i in range(len(top_n_features)):
        col_name = top_n_features[i]
        col = contributions[permutation, col_name]
        dens = _density(col)
        plt.scatter(
            col,
            i + dens * np.random.uniform(-jitter, jitter, size=len(col)),
            alpha=alpha,
            c=_uniformize(frame, col_name)[permutation]
            if colorize_factors or not frame.isfactor(col_name)
            else np.full(frame.nrow, 0.5),
            cmap=colormap
        )
        plt.clim(0, 1)
    cbar = plt.colorbar()
    cbar.set_label('Normalized feature value', rotation=270)
    cbar.ax.get_yaxis().labelpad = 15
    plt.yticks(range(len(top_n_features)), top_n_features)
    plt.xlabel("SHAP value")
    plt.ylabel("Feature")
    plt.title("SHAP Summary plot for \"{}\"".format(model.model_id))
    plt.tight_layout()
    fig = plt.gcf()
    if save_plot_path is not None:
        plt.savefig(fname=save_plot_path)
    return decorate_plot_result(figure=fig)


def shap_explain_row_plot(
        model,  # type: h2o.model.ModelBase
        frame,  # type: h2o.H2OFrame
        row_index,  # type: int
        columns=None,  # type: Optional[Union[List[int], List[str]]]
        top_n_features=10,  # type: int
        figsize=(16, 9),  # type: Union[List[float], Tuple[float]]
        plot_type="barplot",  # type: str
        contribution_type="both",  # type: str
        save_plot_path=None # type: Optional[str]
):  # type: (...) -> plt.Figure
    """
    SHAP local explanation.

    SHAP explanation shows the contribution of features for a given instance. The sum
    of the feature contributions and the bias term is equal to the raw prediction
    of the model (i.e. the prediction before applying inverse link function). H2O implements
    TreeSHAP which, when the features are correlated, can increase the contribution of a feature
    that had no influence on the prediction.

    :param model: h2o tree model, such as DRF, XRT, GBM, XGBoost.
    :param frame: H2OFrame.
    :param row_index: row index of the instance to inspect.
    :param columns: either a list of columns or column indices to show. If specified
                    parameter ``top_n_features`` will be ignored.
    :param top_n_features: a number of columns to pick using variable importance (where applicable).
                  When ``plot_type="barplot"``, then ``top_n_features`` will be chosen for each ``contribution_type``.
    :param figsize: figure size; passed directly to matplotlib.
    :param plot_type: either "barplot" or "breakdown".
    :param contribution_type: One of:

        - "positive"
        - "negative"
        - "both"
        
        Used only for ``plot_type="barplot"``.
    :param save_plot_path: a path to save the plot via using matplotlib function savefig.
    :returns: object that contains the resulting matplotlib figure (can be accessed using ``result.figure()``).

    :examples:
    
    >>> import h2o
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train a GBM
    >>> gbm = H2OGradientBoostingEstimator()
    >>> gbm.train(y=response, training_frame=train)
    >>>
    >>> # Create SHAP row explanation plot
    >>> gbm.shap_explain_row_plot(test, row_index=0)
    """
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)

    if top_n_features < 0:
        top_n_features = float("inf")

    row = frame[row_index, :]
    with no_progress_block():
        contributions = NumpyFrame(model.predict_contributions(row))
    contribution_names = contributions.columns
    prediction = float(contributions.sum(axis=1))
    bias = float(contributions["BiasTerm"])
    contributions = sorted(filter(lambda pair: pair[0] != "BiasTerm", contributions.items()),
                           key=lambda pair: -abs(pair[1]))

    if plot_type == "barplot":
        with no_progress_block():
            prediction = model.predict(row)[0, "predict"]
        row = NumpyFrame(row)

        if contribution_type == "both":
            contribution_type = ["positive", "negative"]
        else:
            contribution_type = [contribution_type]

        if columns is None:
            picked_features = []
            if "positive" in contribution_type:
                positive_features = sorted(filter(lambda pair: pair[1] >= 0, contributions),
                                           key=lambda pair: pair[1])
                picked_features.extend(positive_features[-min(top_n_features, len(positive_features)):])
            if "negative" in contribution_type:
                negative_features = sorted(filter(lambda pair: pair[1] < 0, contributions),
                                           key=lambda pair: pair[1])
                picked_features.extend(negative_features[:min(top_n_features, len(negative_features))])
        else:
            columns = [frame.columns[col] if isinstance(col, int) else col for col in columns]
            picked_cols = []
            for feature in columns:
                if feature in contribution_names:
                    picked_cols.append(feature)
                else:
                    for contrib in contribution_names:
                        if contrib.startswith(feature + "."):
                            picked_cols.append(contrib)
            picked_features = [pair for pair in contributions if pair[0] in picked_cols]

        picked_features = sorted(picked_features, key=lambda pair: pair[1])

        if len(picked_features) < len(contributions):
            contribution_subset_note = " using {} out of {} contributions".format(
                len(picked_features), len(contributions))
        else:
            contribution_subset_note = ""
        contributions = dict(
            feature=np.array(
                ["{}={}".format(pair[0],
                                (_mpl_datetime_to_str(row.get(pair[0])[0])
                                 if pair[0] in frame.columns and frame.type(pair[0]) == "time"
                                 else str(row.get(pair[0])[0])))
                 for pair in picked_features]),
            value=np.array([pair[1][0] for pair in picked_features])
        )
        plt.figure(figsize=figsize)
        plt.barh(range(contributions["feature"].shape[0]), contributions["value"], fc="#b3ddf2")
        plt.grid(True)
        plt.axvline(0, c="black")
        plt.xlabel("SHAP value")
        plt.ylabel("Feature")
        plt.yticks(range(contributions["feature"].shape[0]), contributions["feature"])
        plt.title("SHAP explanation for \"{}\" on row {}{}\nprediction: {}".format(
            model.model_id,
            row_index,
            contribution_subset_note,
            prediction
        ))
        plt.gca().set_axisbelow(True)
        plt.tight_layout()
        fig = plt.gcf()
        if save_plot_path is not None:
            plt.savefig(fname=save_plot_path)
        return decorate_plot_result(figure=fig)

    elif plot_type == "breakdown":
        if columns is None:
            if top_n_features + 1 < len(contributions):
                contributions = contributions[:top_n_features] + [
                    ("Remaining Features", sum(map(lambda pair: pair[1], contributions[top_n_features:])))]
        else:
            picked_cols = []
            columns = [frame.columns[col] if isinstance(col, int) else col for col in columns]
            for feature in columns:
                if feature in contribution_names:
                    picked_cols.append(feature)
                else:
                    for contrib in contribution_names:
                        if contrib.startswith(feature + "."):
                            picked_cols.append(contrib)
            rest = np.array(sum(pair[1] for pair in contributions if pair[0] not in picked_cols))
            contributions = [pair for pair in contributions if pair[0] in picked_cols]
            if len(contribution_names) - 1 > len(picked_cols):  # Contribution names contain "BiasTerm" as well
                contributions += [("Remaining Features", rest)]

        contributions = contributions[::-1]
        contributions = dict(
            feature=np.array([pair[0] for pair in contributions]),
            value=np.array([pair[1][0] for pair in contributions]),
            color=np.array(["g" if pair[1] >= 0 else "r" for pair in contributions])
        )

        contributions["cummulative_value"] = [bias] + list(
            contributions["value"].cumsum()[:-1] + bias)

        plt.figure(figsize=figsize)
        plt.barh(contributions["feature"], contributions["value"],
                 left=contributions["cummulative_value"],
                 color=contributions["color"])
        plt.axvline(prediction, label="Prediction")
        plt.axvline(bias, linestyle="dotted", color="gray", label="Bias")
        plt.vlines(contributions["cummulative_value"][1:],
                   ymin=[y - 0.4 for y in range(contributions["value"].shape[0] - 1)],
                   ymax=[y + 1.4 for y in range(contributions["value"].shape[0] - 1)],
                   color="k")

        plt.legend()
        plt.grid(True)
        xlim = plt.xlim()
        xlim_diff = xlim[1] - xlim[0]
        plt.xlim((xlim[0] - 0.02 * xlim_diff, xlim[1] + 0.02 * xlim_diff))
        plt.xlabel("SHAP value")
        plt.ylabel("Feature")
        plt.gca().set_axisbelow(True)
        plt.tight_layout()
        fig = plt.gcf()
        if save_plot_path is not None:
            plt.savefig(fname=save_plot_path)
        return decorate_plot_result(figure=fig)


def _get_top_n_levels(column, top_n):
    # type: (h2o.H2OFrame, int) -> List[str]
    """
    Get top_n levels from factor column based on their frequency.

    :param column: string containing column name
    :param top_n: maximum number of levels to be returned
    :returns: list of levels
    """
    counts = column.table().sort("Count", ascending=[False])[:, 0]
    return [
        level[0]
        for level in counts[:min(counts.nrow, top_n), :].as_data_frame(
            use_pandas=False, header=False)
    ]


def _factor_mapper(mapping):
    # type: (Dict) -> Callable
    """
    Helper higher order function returning function that applies mapping to each element.
    :param mapping: dictionary that maps factor names to floats (for NaN; other values are integers)
    :returns: function to be applied on iterable
    """

    def _(column):
        return [mapping.get(entry, float("nan")) for entry in column]

    return _


def _add_histogram(frame, column, add_rug, add_histogram=True, levels_order=None):
    # type: (H2OFrame, str, bool, bool) -> None
    """
    Helper function to add rug and/or histogram to a plot
    :param frame: H2OFrame
    :param column: string containing column name
    :param add_rug: if True, adds rug
    :param add_histogram: if True, adds histogram
    :returns: None
    """
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
    ylims = plt.ylim()
    nf = NumpyFrame(frame[column])

    if nf.isfactor(column) and levels_order is not None:
        new_mapping = dict(zip(levels_order, range(len(levels_order))))
        mapping = _factor_mapper({k: new_mapping[v] for k, v in nf.from_num_to_factor(column).items()})
    else:
        def mapping(x):
            return x

    if add_rug:
        plt.plot(mapping(nf[column]),
                 [ylims[0] for _ in range(frame.nrow)],
                 "|", color="k", alpha=0.2, ms=20)
    if add_histogram:
        if nf.isfactor(column):
            cnt = Counter(nf[column][np.isfinite(nf[column])])
            hist_x = np.array(list(cnt.keys()), dtype=float)
            hist_y = np.array(list(cnt.values()), dtype=float)
            width = 1
        else:
            hist_y, hist_x = np.histogram(
                mapping(nf[column][np.isfinite(nf[column])]),
                bins=20)
            hist_x = hist_x[:-1].astype(float)
            hist_y = hist_y.astype(float)
            width = hist_x[1] - hist_x[0]
        plt.bar(mapping(hist_x),
                hist_y / hist_y.max() * ((ylims[1] - ylims[0]) / 1.618),  # ~ golden ratio
                bottom=ylims[0],
                align="center" if nf.isfactor(column) else "edge",
                width=width, color="gray", alpha=0.2)

    if nf.isfactor(column):
        plt.xticks(mapping(range(nf.nlevels(column))), nf.levels(column))
    elif frame.type(column) == "time":
        import matplotlib.dates as mdates
        xmin = np.nanmin(nf[column])
        xmax = np.nanmax(nf[column])
        offset = (xmax - xmin) / 50
        # hardcoding the limits to prevent calculating negative date
        # happens sometimes when the matplotlib decides to show the origin in the plot
        # and gives hard to decode errors
        plt.xlim(max(0, xmin - offset), xmax + offset)
        locator = mdates.AutoDateLocator()
        formatter = mdates.AutoDateFormatter(locator)
        plt.gca().xaxis.set_major_locator(locator)
        plt.gca().xaxis.set_major_formatter(formatter)
        plt.gcf().autofmt_xdate()
    plt.ylim(ylims)


def _append_graphing_data(graphing_data, data_to_append, original_observation_value, frame_id, centered, show_logoods,
                          row_id, **kwargs):
    """
    Returns a table (H2OTwoDimTable) in output form required when output_graphing_data = True. Contains provided graphing_data
    table content expanded by data extracted from data_to_append table and formed to fit into graphing_data form
    (columns, types). Input tables output_graphing_data and graphing_data stay unchanged, returned expanded table is a
    new H2OTwoDimTable instance.

    If graphing_data is None, only data_to_append table content is extracted and together with other input information
    is formed into new output table of required form.

    If data_to_append is None, there is notheng to extract and append so original graphing_data is returned.

    :param graphing_data: H2OTwoDimTable, table to be returned when output_graphing_data = True
    :param data_to_append: H2OTwoDimTable, table that contains new data to be extracted and appended to graphing_data in
     the new resulting table
    :param original_observation_value: original observation value of current ICE line
    :param frame_id: string, identificator of sample on which current ICE line is being calculated
    :param centered: boolean, whether centering is turned on/off for current ICE line calculation
    :param show_logoods: boolean, whether logoods calculation is turned on/off for current ICE line
    :param row_id: int, identification of the row of sample on which current ICE line is being calculated

    :returns: H2OTwoDimTable table
    """
    grouping_variable_value = kwargs.get("grouping_variable_value")
    response_type = data_to_append.col_types[data_to_append.col_header.index("mean_response")]
    grouping_variable_type = "string" if type(grouping_variable_value) is str else "double" # todo test this
    bin_type = data_to_append.col_types[0]

    col_header = ["sample_id", "row_id", "column", "mean_response", "simulated_x_value", "is_original_observation"]
    col_types = ["string", "int", "string", response_type, bin_type, "bool"]
    table_header = "ICE plot graphing output" + kwargs.get("group_label", "")

    if grouping_variable_value is not None:
        col_header.append("grouping_variable_value")
        col_types.append(grouping_variable_type)
    centering_value = None
    if centered:
        col_header.append("centered_response")
        col_types.append(response_type)
        centering_value = data_to_append['mean_response'][0]
    if show_logoods:
        col_header.append("log(odds)")
        col_types.append(response_type)

    if graphing_data is None:
        return h2o.two_dim_table.H2OTwoDimTable(col_header=col_header, col_types=col_types, table_header=table_header,
                                                cell_values=_extract_graphing_data_values(data_to_append, frame_id,
                                                                                          grouping_variable_value,
                                                                                          original_observation_value,
                                                                                          centering_value, show_logoods,
                                                                                          row_id))
    if data_to_append is None:
        return graphing_data

    new_values = graphing_data._cell_values + _extract_graphing_data_values(data_to_append, frame_id,
                                                                            grouping_variable_value,
                                                                            original_observation_value, centering_value,
                                                                            show_logoods, row_id)
    return h2o.two_dim_table.H2OTwoDimTable(col_header=graphing_data.col_header, col_types=graphing_data.col_types,
                                            cell_values=new_values, table_header=table_header)


def _extract_graphing_data_values(data, frame_id, grouping_variable_value, original_observation, centering_value,
                                  show_logodds, row_id):
    res_data = []
    column = data.col_header[0]
    for row in data.cell_values:
        new_row = [frame_id, row_id, column, row[1], row[0], original_observation == row[0]]
        if grouping_variable_value is not None:
            new_row.append(grouping_variable_value)
        if centering_value is not None:
            new_row.append(row[1] - centering_value)
        if show_logodds:
            new_row.append(np.log(row[1] / (1 - row[1])))
        res_data.append(new_row)
    return res_data


def _handle_ice(model, frame, colormap, plt, target, is_factor, column, show_logodds, centered, factor_map, show_pdp,
                output_graphing_data, nbins, show_rug, **kwargs):
    frame = frame.sort(model.actual_params["response_column"])
    deciles = [int(round((frame.nrow - 1) * dec / 10)) for dec in range(11)]
    colors = plt.get_cmap(colormap, 11)(list(range(11)))
    data = None
    for i, index in enumerate(deciles):
        percentile_string = "{}th Percentile".format(i * 10)
        pd_data = model.partial_plot(
            frame,
            cols=[column],
            plot=False,
            row_index=index,
            targets=target,
            nbins=nbins if not is_factor else (1 + frame[column].nlevels()[0]),
            include_na=True
        )[0]
        tmp = NumpyFrame(pd_data)
        y_label = "Response"
        if not is_factor and centered:
            y_label = "Response difference"
            _center(tmp["mean_response"])
        if show_logodds:
            y_label = "log(odds)"

        encoded_col = tmp.columns[0]
        orig_value = frame.as_data_frame(use_pandas=False, header=False)[index][frame.col_names.index(column)]
        orig_vals = _handle_orig_values(is_factor, pd_data, encoded_col, plt, target, model,
                                        frame, index, column, colors[i], percentile_string, factor_map, orig_value)
        orig_row = NumpyFrame(orig_vals)
        if frame.type(column) == "time":
            tmp[encoded_col] = _timestamp_to_mpl_datetime(tmp[encoded_col])
            orig_row[encoded_col] = _timestamp_to_mpl_datetime(orig_row[encoded_col])
        if output_graphing_data:
            data = _append_graphing_data(data, pd_data, frame[index, column], frame.frame_id,
                                         not is_factor and centered, show_logodds, index, **kwargs)
            if (not is_factor or not frame[index, column] in data["simulated_x_value"]) and not _isnan(frame[index, column]): #nan is already there
                data = _append_graphing_data(data, orig_vals, frame[index, column], frame.frame_id,
                                             not is_factor and centered, show_logodds, index, **kwargs)
        if not _isnan(orig_value) or orig_value != '':
            tmp._data = np.append(tmp._data, orig_row._data, axis=0)
        if is_factor:
            response = _get_response(tmp["mean_response"], show_logodds)
            plt.scatter(factor_map(tmp.get(encoded_col)),
                        response,
                        color=[colors[i]],
                        label=percentile_string)
        else:
            tmp._data = tmp._data[tmp._data[:,0].argsort()]
            response = _get_response(tmp["mean_response"], show_logodds)
            plt.plot(tmp[encoded_col],
                     response,
                     color=colors[i],
                     label=percentile_string)

    if show_pdp:
        tmp = NumpyFrame(
            model.partial_plot(
                frame,
                cols=[column],
                plot=False,
                targets=target,
                nbins=nbins if not is_factor else (1 + frame[column].nlevels()[0])
            )[0]
        )
        encoded_col = tmp.columns[0]
        if frame.type(column) == "time":
            tmp[encoded_col] = _timestamp_to_mpl_datetime(tmp[encoded_col])
        response = _get_response(tmp["mean_response"], show_logodds)
        if not is_factor and centered:
            _center(tmp["mean_response"])
        if is_factor:
            plt.scatter(factor_map(tmp.get(encoded_col)), response, color="k",
                        label="Partial Dependence")
        else:
            plt.plot(tmp[encoded_col], response, color="k", linestyle="dashed",
                     label="Partial Dependence")

    _add_histogram(frame, column, add_rug=show_rug)
    plt.title("Individual Conditional Expectation for \"{}\"\non column \"{}\"{}{}".format(
        model.model_id,
        column,
        " with target = \"{}\"".format(target[0]) if target else "",
        kwargs.get("group_label", "")
    ))
    plt.xlabel(column)
    plt.ylabel(y_label)

    ax = plt.gca()
    box = ax.get_position()
    ax.set_position([box.x0, box.y0, box.width * 0.8, box.height])
    # add custom legend for Original observations:
    handles, labels = ax.get_legend_handles_labels()
    patch = plt.plot([],[], marker="o", alpha=0.5, ms=10, ls="", mec=None, color='grey',
                     label="Original observations")[0]
    handles.append(patch)
    plt.legend(handles=handles, loc='center left', bbox_to_anchor=(1, 0.5))
    ax.legend(loc='center left', bbox_to_anchor=(1, 0.5))
    plt.grid(True)
    if is_factor:
        plt.xticks(rotation=45, rotation_mode="anchor", ha="right")
    plt.tight_layout(rect=[0, 0, 0.85, 1])
    fig = plt.gcf()
    return fig, data


def _handle_pdp(model, frame, colormap, plt, target, is_factor, column, show_logodds, factor_map, row_index, row_value,
                output_graphing_data, nbins, show_rug, **kwargs):
    color = plt.get_cmap(colormap)(0)
    data = model.partial_plot(frame, cols=[column], plot=False,
                              row_index=row_index, targets=target,
                              nbins=nbins if not is_factor else (1 + frame[column].nlevels()[0]))[0]
    tmp = NumpyFrame(data)
    encoded_col = tmp.columns[0]
    if frame.type(column) == "time":
        tmp[encoded_col] = _timestamp_to_mpl_datetime(tmp[encoded_col])
    response = _get_response(tmp["mean_response"], show_logodds)
    stddev_response = _get_stddev_response(tmp["stddev_response"], tmp["mean_response"], show_logodds)

    if is_factor:
        plt.errorbar(factor_map(tmp.get(encoded_col)), response,
                     yerr=stddev_response, fmt='o', color=color,
                     ecolor=color, elinewidth=3, capsize=0, markersize=10)
    else:
        plt.plot(tmp[encoded_col], response, color=color)
        plt.fill_between(tmp[encoded_col], response - stddev_response,
                         response + stddev_response, color=color, alpha=0.2)

    _add_histogram(frame, column, add_rug=show_rug)

    if row_index is None:
        plt.title("Partial Dependence plot for \"{}\"{}{}".format(
            column,
            " with target = \"{}\"".format(target[0]) if target else "",
            kwargs.get("group_label", "")
        ))
        plt.ylabel("log(odds)" if show_logodds else "Mean Response")
    else:
        if is_factor:
            plt.axvline(factor_map([row_value]), c="k", linestyle="dotted",
                        label="Instance value")
        else:
            row_val = row_value
            if frame.type(column) == "time":
                row_val = _timestamp_to_mpl_datetime(row_val)
            plt.axvline(row_val, c="k", linestyle="dotted", label="Instance value")
        plt.title("Individual Conditional Expectation for column \"{}\" and row {}{}{}".format(
            column,
            row_index,
            " with target = \"{}\"".format(target[0]) if target else "",
            kwargs.get("group_label", "")
        ))
        plt.ylabel("log(odds)" if show_logodds else "Response")
    ax = plt.gca()
    box = ax.get_position()
    ax.set_position([box.x0, box.y0, box.width * 0.8, box.height])
    plt.xlabel(column)
    plt.grid(True)
    if is_factor:
        plt.xticks(rotation=45, rotation_mode="anchor", ha="right")
    plt.tight_layout()
    fig = plt.gcf()
    return fig, data if output_graphing_data else None

def pd_ice_common(
        model,  # type: h2o.model.model_base.ModelBase
        frame,  # type: h2o.H2OFrame
        column,  # type: str
        row_index=None,  # type: Optional[int]
        target=None,  # type: Optional[str]
        max_levels=30,  # type: int
        figsize=(16, 9),  # type: Union[Tuple[float], List[float]]
        colormap="Dark2",  # type: str
        save_plot_path=None, # type: Optional[str]
        show_pdp=True,  # type: bool
        binary_response_scale="response", # type: Literal["response", "logodds"]
        centered=False, # type: bool
        is_ice=False, # type: bool
        grouping_column=None,  # type: Optional[str]
        output_graphing_data=False, # type: bool
        nbins=100, # type: int
        show_rug=True,  # type: bool
        **kwargs
):
    """
    Common base for partial dependence plot and ice plot.

    :param model: H2O Model object
    :param frame: H2OFrame
    :param column: string containing column name
    :param row_index: if None, do partial dependence, if integer, do individual
                      conditional expectation for the row specified by this integer
    :param target: (only for multinomial classification) for what target should the plot be done
    :param max_levels: maximum number of factor levels to show
    :param figsize: figure size; passed directly to matplotlib
    :param colormap: colormap name; used to get just the first color to keep the api and color scheme similar with
                     pd_multi_plot
    :param save_plot_path: a path to save the plot via using matplotlib function savefig
    :param show_pdp: option to turn on/off PDP line. Defaults to True.
    :param binary_response_scale: option for binary model to display (on the y-axis) the logodds instead of the actual
    :param centered: a bool that determines whether to center curves around 0 at the first valid x value or not
    :param is_ice: a bool that determines whether the caller of this method is ice_plot or pd_plot
    :param grouping_column A feature column name to group the data and provide separate sets of plots
                           by grouping feature values
    :param output_graphing_data: a bool that determines whether to output final graphing data to a frame
    :param nbins: Number of bins used.
    :param show_rug: Show rug to visualize the density of the column
    :returns: object that contains the resulting matplotlib figure (can be accessed using result.figure())

    """
    for kwarg in kwargs:
        if kwarg not in ['grouping_variable_value', 'group_label']:
            raise TypeError('Unknown keyword argument:', kwarg)

    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)

    if frame.type(column) == "string":
        raise ValueError("String columns are not supported!")

    is_factor = frame[column].isfactor()[0]

    if target is not None:
        if isinstance(target, (list, tuple)):
            if len(target) > 1:
                raise ValueError("Only one target can be specified!")
            target = target[0]
        target = [target]

    if grouping_column is not None:
        return _handle_grouping(frame, grouping_column, save_plot_path, model, column, target, max_levels, figsize,
                                colormap, is_ice, row_index, show_pdp, binary_response_scale, centered,
                                output_graphing_data, nbins)

    factor_map = None
    row_value = frame[row_index, column] if row_index is not None else None # get the value before we filter out irrelevant categories
    if is_factor:
        if centered:
            warnings.warn("Centering is not supported for factor columns!")
        if frame[column].nlevels()[0] > max_levels:
            levels = _get_top_n_levels(frame[column], max_levels)
            if row_index is not None:
                levels = list(set(levels + [frame[row_index, column]]))
            frame = frame[(frame[column].isin(levels)), :]
            # decrease the number of levels to the actual number of levels in the subset
            frame[column] = frame[column].ascharacter().asfactor()
        factor_map = _factor_mapper(NumpyFrame(frame[column]).from_factor_to_num(column))

    is_binomial = _is_binomial(model)
    if (not is_binomial) and (binary_response_scale == "logodds"):
        raise ValueError("binary_response_scale cannot be set to 'logodds' value for non-binomial models!")

    if binary_response_scale not in ["logodds", "response"]:
        raise ValueError("Unsupported value for binary_response_scale!")

    show_logodds = is_binomial and binary_response_scale == "logodds"

    with no_progress_block():
        plt.figure(figsize=figsize)
        if is_ice:
            res = _handle_ice(model, frame, colormap, plt, target, is_factor, column, show_logodds, centered,
                              factor_map,
                              show_pdp, output_graphing_data, nbins, show_rug=show_rug, **kwargs)
        else:
            res = _handle_pdp(model, frame, colormap, plt, target, is_factor, column, show_logodds, factor_map,
                              row_index, row_value, output_graphing_data, nbins, show_rug=show_rug, **kwargs)

        if save_plot_path is not None:
            plt.savefig(fname=save_plot_path)
        return decorate_plot_result(figure=res[0], res=res[1])

def pd_plot(
        model,  # type: h2o.model.model_base.ModelBase
        frame,  # type: h2o.H2OFrame
        column,  # type: str
        row_index=None,  # type: Optional[int]
        target=None,  # type: Optional[str]
        max_levels=30,  # type: int
        figsize=(16, 9),  # type: Union[Tuple[float], List[float]]
        colormap="Dark2",  # type: str
        save_plot_path=None, # type: Optional[str]
        binary_response_scale="response", # type: Literal["response", "logodds"]
        grouping_column=None,  # type: Optional[str]
        output_graphing_data=False,  # type: bool
        nbins=100,  # type: int
        show_rug=True,  # type: bool
        **kwargs
):
    """
    Plot partial dependence plot.

    The partial dependence plot (PDP) provides a graph of the marginal effect of a variable
    on the response. The effect of a variable is measured by the change in the mean response.
    The PDP assumes independence between the feature for which is the PDP computed and the rest.

    :param model: H2O Model object.
    :param frame: H2OFrame.
    :param column: string containing column name.
    :param row_index: if None, do partial dependence; if integer, do individual
                      conditional expectation for the row specified by this integer.
    :param target: (only for multinomial classification) for what target should the plot be done.
    :param max_levels: maximum number of factor levels to show.
    :param figsize: figure size; passed directly to matplotlib.
    :param colormap: colormap name; used to get just the first color to keep the api and color scheme similar with
                     ``pd_multi_plot``.
    :param save_plot_path: a path to save the plot via using matplotlib function savefig.
    :param binary_response_scale: option for binary model to display (on the y-axis) the logodds instead of the actual score.
        Can be one of: "response" (default), "logodds".
    :param grouping_column: A feature column name to group the data and provide separate sets of plots
                           by grouping feature values.
    :param output_graphing_data: a bool that determines whether to output final graphing data to a frame.
    :param nbins: Number of bins used.
    :param show_rug: Show rug to visualize the density of the column
    :returns: object that contains the resulting matplotlib figure (can be accessed using ``result.figure()``).

    :examples:
    
    >>> import h2o
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train a GBM
    >>> gbm = H2OGradientBoostingEstimator()
    >>> gbm.train(y=response, training_frame=train)
    >>>
    >>> # Create partial dependence plot
    >>> gbm.pd_plot(test, column="alcohol")
    """
    return pd_ice_common(model, frame, column, row_index, target, max_levels, figsize, colormap, save_plot_path,
                         True, binary_response_scale, None, False, grouping_column, output_graphing_data, nbins,
                         show_rug=show_rug, **kwargs)



def pd_multi_plot(
        models,  # type: Union[h2o.automl._base.H2OAutoMLBaseMixin, h2o.H2OFrame, List[h2o.model.model_base]]
        frame,  # type: h2o.H2OFrame
        column,  # type: str
        best_of_family=True,  # type: bool
        row_index=None,  # type: Optional[int]
        target=None,  # type: Optional[str]
        max_levels=30,  # type: int
        figsize=(16, 9),  # type: Union[Tuple[float], List[float]]
        colormap="Dark2",  # type: str
        markers=["o", "v", "s", "P", "*", "D", "X", "^", "<", ">", "."],  # type: List[str]
        save_plot_path=None,  # type: Optional[str]
        show_rug=True  # type: bool
):  # type: (...) -> plt.Figure
    """
    Plot partial dependencies of a variable across multiple models.

    The partial dependence plot (PDP) provides a graph of the marginal effect of a variable
    on the response. The effect of a variable is measured by the change in the mean response.
    The PDP assumes independence between the feature for which is the PDP computed and the rest.

    :param models: a list of H2O models, an H2O AutoML instance, or an H2OFrame with a 'model_id' column (e.g. H2OAutoML leaderboard)
    :param frame: H2OFrame
    :param column: string containing column name
    :param best_of_family: if True, show only the best models per family
    :param row_index: if None, do partial dependence, if integer, do individual
                      conditional expectation for the row specified by this integer
    :param target: (only for multinomial classification) for what target should the plot be done
    :param max_levels: maximum number of factor levels to show
    :param figsize: figure size; passed directly to matplotlib
    :param colormap: colormap name
    :param markers: List of markers to use for factors, when it runs out of possible markers the last in
                    this list will get reused
    :param save_plot_path: a path to save the plot via using matplotlib function savefig
    :param show_rug: Show rug to visualize the density of the column
    :returns: object that contains the resulting matplotlib figure (can be accessed using ``result.figure()``).

    :examples:
    
    >>> import h2o
    >>> from h2o.automl import H2OAutoML
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train an H2OAutoML
    >>> aml = H2OAutoML(max_models=10)
    >>> aml.train(y=response, training_frame=train)
    >>>
    >>> # Create a partial dependence plot
    >>> aml.pd_multi_plot(test, column="alcohol")
    """
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
    if target is not None:
        if isinstance(target, (list, tuple)):
            if len(target) > 1:
                raise ValueError("Only one target can be specified!")
            target = target[0]
        target = [target]

    if frame.type(column) == "string":
        raise ValueError("String columns are not supported!")

    if _is_automl_or_leaderboard(models):
        all_models = _get_model_ids_from_automl_or_leaderboard(models)
    else:
        all_models = models

    is_factor = frame[column].isfactor()[0]
    if is_factor:
        if frame[column].nlevels()[0] > max_levels:
            levels = _get_top_n_levels(frame[column], max_levels)
            if row_index is not None:
                levels = list(set(levels + [frame[row_index, column]]))
            frame = frame[(frame[column].isin(levels)), :]
            # decrease the number of levels to the actual number of levels in the subset
            frame[column] = frame[column].ascharacter().asfactor()
    if best_of_family:
        models = _first_of_family(all_models)
    else:
        models = all_models

    models = [m if isinstance(m, h2o.model.ModelBase) else h2o.get_model(m) for m in models]

    colors = plt.get_cmap(colormap, len(models))(list(range(len(models))))
    with no_progress_block():
        plt.figure(figsize=figsize)
        is_factor = frame[column].isfactor()[0]
        if is_factor:
            factor_map = _factor_mapper(NumpyFrame(frame[column]).from_factor_to_num(column))
            marker_map = dict(zip(range(len(markers) - 1), markers[:-1]))
        model_ids = _shorten_model_ids([model.model_id for model in models])
        for i, model in enumerate(models):
            tmp = NumpyFrame(
                model.partial_plot(frame, cols=[column], plot=False,
                                   row_index=row_index, targets=target,
                                   nbins=20 if not is_factor else 1 + frame[column].nlevels()[0])[0])
            encoded_col = tmp.columns[0]
            if frame.type(column) == "time":
                tmp[encoded_col] = _timestamp_to_mpl_datetime(tmp[encoded_col])
            if is_factor:
                plt.scatter(factor_map(tmp.get(encoded_col)), tmp["mean_response"],
                            color=[colors[i]], label=model_ids[i],
                            marker=marker_map.get(i, markers[-1]))
            else:
                plt.plot(tmp[encoded_col], tmp["mean_response"], color=colors[i],
                         label=model_ids[i])

        _add_histogram(frame, column, add_rug=show_rug)

        if row_index is None:
            plt.title("Partial Dependence plot for \"{}\"{}".format(
                column,
                " with target = \"{}\"".format(target[0]) if target else ""
            ))
            plt.ylabel("Mean Response")
        else:
            if is_factor:
                plt.axvline(factor_map([frame[row_index, column]]), c="k", linestyle="dotted",
                            label="Instance value")
            else:
                plt.axvline(frame[row_index, column], c="k", linestyle="dotted",
                            label="Instance value")
            plt.title("Individual Conditional Expectation for column \"{}\" and row {}{}".format(
                column,
                row_index,
                " with target = \"{}\"".format(target[0]) if target else ""
            ))
            plt.ylabel("Response")

        ax = plt.gca()
        box = ax.get_position()
        ax.set_position([box.x0, box.y0, box.width * 0.8, box.height])
        ax.legend(loc='center left', bbox_to_anchor=(1, 0.5))
        plt.xlabel(column)
        plt.grid(True)
        if is_factor:
            plt.xticks(rotation=45, rotation_mode="anchor", ha="right")
        plt.tight_layout(rect=[0, 0, 0.8, 1])
        fig = plt.gcf()
        if save_plot_path is not None:
            plt.savefig(fname=save_plot_path)
        return decorate_plot_result(figure=fig)

def _center(col):
    col[:] = col - col[0]

def _prepare_grouping_frames(frame, grouping_column):
    _MAX_GROUPING_FRAME_CARDINALITY = 10
    if grouping_column not in frame.names:
        raise ValueError("Grouping variable '" + grouping_column + "' is not present in frame!")
    if not frame[grouping_column].isfactor()[0]:
        raise ValueError("Grouping variable has to be categorical!")
    categories = frame[grouping_column].categories()
    if len(categories) > _MAX_GROUPING_FRAME_CARDINALITY:
        raise ValueError("Grouping column option is supported only for variables with 10 or fewer levels!")
    frames = list()
    for i, curr_category in enumerate(categories):
        key = "tmp_{}{}".format(curr_category, str(i))
        expr = "(tmp= {} (rows {} (==(cols {} [{}] ) '{}') ))".format(key, frame.frame_id, frame.frame_id, str(frame.names.index(grouping_column)), curr_category)
        h2o.rapids(expr)
        frames.append(h2o.get_frame(key))
    return frames


def _handle_grouping(frame, grouping_column, save_plot_path, model, column, target, max_levels, figsize, colormap, is_ice, row_index, show_pdp, binary_response_scale, centered, output_graphing_data, nbins):
    frames = _prepare_grouping_frames(frame, grouping_column)
    result = list()
    for i, curr_frame in enumerate(frames):
        curr_category = frame[grouping_column].categories()[i]
        curr_save_plot_path = None
        if save_plot_path is not None:
            root_path, ext = os.path.splitext(save_plot_path)
            curr_save_plot_path = root_path + "_" + curr_category + ext
        group_label = "\ngrouping variable: {} = '{}'".format(grouping_column, curr_category)
        if is_ice:
            plot = ice_plot(
                model,
                curr_frame,
                column,
                target,
                max_levels,
                figsize,
                colormap,
                curr_save_plot_path,
                show_pdp,
                binary_response_scale,
                centered,
                grouping_column=None,
                output_graphing_data=output_graphing_data,
                nbins=nbins,
                group_label=group_label,
                grouping_variable_value=curr_category
            )
        else:
            plot = pd_plot(
                model,
                frame,
                column,
                row_index,
                target,
                max_levels,
                figsize,
                colormap,
                curr_save_plot_path,
                binary_response_scale,
                grouping_column=None,
                output_graphing_data=output_graphing_data,
                nbins=nbins,
                group_label=group_label,
                grouping_variable_value=curr_category
            )
        result.append(plot)
        h2o.remove(curr_frame.key, False)
    return result


def _handle_orig_values(is_factor, pd_data, encoded_col, plt, target, model, frame,
                        index, column, color, percentile_string, factor_map, orig_value):
    PDP_RESULT_FACTOR_NAN_MARKER = '.missing(NA)'
    tmp = NumpyFrame(pd_data)
    user_splits = dict()
    if _isnan(orig_value) or orig_value == "":
        if is_factor:
            idx = np.where(tmp[encoded_col] == tmp.from_factor_to_num(encoded_col)[PDP_RESULT_FACTOR_NAN_MARKER])[0][0]
        else:
            idx = np.where(np.isnan(tmp[encoded_col]))[0][0]
        # orig_null_value = tmp.from_num_to_factor(encoded_col)[tmp[encoded_col][idx]] if is_factor else tmp[encoded_col][idx]
        orig_null_value = PDP_RESULT_FACTOR_NAN_MARKER if is_factor else np.nan
        percentile_string = "for " + percentile_string if percentile_string is not None else ""
        msg = "Original observation of \"{}\" {} is [{}, {}]. Plotting of NAs is not yet supported.".format(encoded_col,
                                                                                                            percentile_string,
                                                                                                            orig_null_value,
                                                                                                            tmp["mean_response"][idx])
        warnings.warn(msg)
        res_data = h2o.two_dim_table.H2OTwoDimTable(cell_values=[list(pd_data.cell_values[idx])],
                                                    col_header=pd_data.col_header, col_types=pd_data.col_types)
        return res_data
    else:
        user_splits[column] = [str(orig_value)] if is_factor else [orig_value]
        pp_table = model.partial_plot(
            frame,
            cols=[column],
            plot=False,
            row_index=index,
            targets=target,
            user_splits=user_splits,
        )[0]
        orig_tmp = NumpyFrame(pp_table)
        if is_factor:
            # preserve the same factor-to-num mapping
            orig_tmp._data[0,0] = factor_map([orig_value])[0]
        plt.scatter(orig_tmp[encoded_col], orig_tmp["mean_response"],
                    color=[color], marker='o', s=150, alpha=0.5)
        return pp_table


def ice_plot(
        model,  # type: h2o.model.ModelBase
        frame,  # type: h2o.H2OFrame
        column,  # type: str
        target=None,  # type: Optional[str]
        max_levels=30,  # type: int
        figsize=(16, 9),  # type: Union[Tuple[float], List[float]]
        colormap="plasma",  # type: str
        save_plot_path=None,  # type: Optional[str]
        show_pdp=True,  # type: bool
        binary_response_scale="response",  # type: Literal["response", "logodds"]
        centered=False,  # type: bool
        grouping_column=None,  # type: Optional[str]
        output_graphing_data=False, #type: bool
        nbins=100,  # type: int
        show_rug=True,  # type: bool
        **kwargs
):  # type: (...) -> plt.Figure
    """
    Plot Individual Conditional Expectations (ICE) for each decile.

    The individual conditional expectations (ICE) plot gives a graphical depiction of the marginal
    effect of a variable on the response. The ICE plot is similar to a partial dependence plot (PDP) because
    a PDP shows the average effect of a feature while ICE plot shows the effect for a single
    instance. The following plot shows the effect for each decile. In contrast to a partial
    dependence plot, the ICE plot can provide more insight especially when there is stronger feature interaction.
    Also, the plot shows the original observation values marked by a semi-transparent circle on each ICE line. Note that
    the score of the original observation value may differ from score value of the underlying ICE line at the original
    observation point as the ICE line is drawn as an interpolation of several points.

    :param model: H2OModel.
    :param frame: H2OFrame.
    :param column: string containing column name.
    :param target: (only for multinomial classification) for what target should the plot be done.
    :param max_levels: maximum number of factor levels to show.
    :param figsize: figure size; passed directly to matplotlib.
    :param colormap: colormap name.
    :param save_plot_path: a path to save the plot via using matplotlib function savefig.
    :param show_pdp: option to turn on/off PDP line. Defaults to ``True``.
    :param binary_response_scale: option for binary model to display (on the y-axis) the logodds instead of the actual
        score. Can be one of: "response" (default) or "logodds".
    :param centered: a bool that determines whether to center curves around 0 at the first valid x value or not.
    :param grouping_column: a feature column name to group the data and provide separate sets of plots by
        grouping feature values.
    :param output_graphing_data: a bool that determmines whether to output final graphing data to a frame.
    :param nbins: Number of bins used.
    :param show_rug: Show rug to visualize the density of the column
    :returns: object that contains the resulting matplotlib figure (can be accessed using ``result.figure()``).

    :examples:
    
    >>> import h2o
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response:
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train a GBM:
    >>> gbm = H2OGradientBoostingEstimator()
    >>> gbm.train(y=response, training_frame=train)
    >>>
    >>> # Create the individual conditional expectations plot:
    >>> gbm.ice_plot(test, column="alcohol")
    """
    return pd_ice_common(model, frame, column, None, target, max_levels, figsize, colormap,
                         save_plot_path, show_pdp, binary_response_scale, centered, True, grouping_column, output_graphing_data, nbins,
                         show_rug=show_rug, **kwargs)


def _is_binomial(model):
    if isinstance(model, h2o.estimators.stackedensemble.H2OStackedEnsembleEstimator):
        return _is_binomial_from_model(model.metalearner())
    else:
        return _is_binomial_from_model(model)


def _is_binomial_from_model(model):
    return model._model_json["output"]["model_category"] == "Binomial"


def _get_response(mean_response, show_logodds):
    if show_logodds:
        return np.log(mean_response / (1 - mean_response))
    else:
        return mean_response


def _get_stddev_response(stdev_response, mean_response, show_logodds):
    if show_logodds:
        return 1 / np.sqrt(len(mean_response) * mean_response * (1 - mean_response))
    else:
        return stdev_response


def _isnan(value):
    if isinstance(value, float):
        return np.isnan(value)
    else:
        return False

def _has_varimp(model):
    # type: (h2o.model.ModelBase) -> bool
    """
    Does model have varimp?
    :param model: model or a string containing model_id
    :returns: bool
    """
    assert isinstance(model, h2o.model.ModelBase)
    # check for cases when variable importance is disabled or
    # when a model is stopped sooner than calculating varimp (xgboost can rarely have no varimp).
    output = model._model_json["output"]
    return output.get("variable_importances") is not None


def _is_automl_or_leaderboard(obj):
    # type: (object) -> bool
    """
    Is obj an H2OAutoML object or a leaderboard?
    :param obj: object to test
    :return: bool
    """
    return (
            isinstance(obj, h2o.automl._base.H2OAutoMLBaseMixin) or
            (isinstance(obj, h2o.H2OFrame) and "model_id" in obj.columns)
    )


def _get_model_ids_from_automl_or_leaderboard(automl_or_leaderboard, filter_=lambda _: True):
    # type: (object) -> List[str]
    """
    Get model ids from H2OAutoML object or leaderboard
    :param automl_or_leaderboard: AutoML
    :return: List[str]
    """
    leaderboard = (automl_or_leaderboard.leaderboard
                   if isinstance(automl_or_leaderboard, h2o.automl._base.H2OAutoMLBaseMixin)
                   else automl_or_leaderboard)
    return [model_id[0] for model_id in leaderboard[:, "model_id"].as_data_frame(use_pandas=False, header=False)
            if filter_(model_id[0])]


def _get_models_from_automl_or_leaderboard(automl_or_leaderboard, filter_=lambda _: True):
    # type: (object) -> Generator[h2o.model.ModelBase, None, None]
    """
    Get model ids from H2OAutoML object or leaderboard
    :param automl_or_leaderboard: AutoML
    :param filter_: a predicate used to filter model_ids. Signature of the filter is (model) -> bool.
    :return: Generator[h2o.model.ModelBase, None, None]
    """
    models = (h2o.get_model(model_id) for model_id in _get_model_ids_from_automl_or_leaderboard(automl_or_leaderboard))
    return (model for model in models if filter_(model))


def _get_xy(model):
    # type: (h2o.model.ModelBase) -> Tuple[List[str], str]
    """
    Get features (x) and the response column (y).
    :param model: H2O Model
    :returns: tuple (x, y)
    """
    names = model._model_json["output"]["original_names"] or model._model_json["output"]["names"]
    y = model.actual_params["response_column"]
    not_x = [
                y,
                # if there is no fold column, fold_column is set to None, thus using "or {}" instead of the second argument of dict.get
                (model.actual_params.get("fold_column") or {}).get("column_name"),
                (model.actual_params.get("weights_column") or {}).get("column_name"),
                (model.actual_params.get("offset_column") or {}).get("column_name"),
            ] + (model.actual_params.get("ignored_columns") or [])
    x = [feature for feature in names if feature not in not_x]
    return x, y


def _consolidate_varimps(model):
    # type (h2o.model.ModelBase) -> Dict
    """
    Get variable importances just for the columns that are present in the data set, i.e.,
    when an encoded variables such as "column_name.level_name" are encountered, those variable
    importances are summed to "column_name" variable.

    :param model: H2O Model
    :returns: dictionary with variable importances
    """
    x, y = _get_xy(model)

    varimp = {line[0]: line[3] for line in model.varimp()}

    consolidated_varimps = {k: v for k, v in varimp.items() if k in x}
    to_process = {k: v for k, v in varimp.items() if k not in x}

    domain_mapping = _get_domain_mapping(model)
    encoded_cols = ["{}.{}".format(name, domain)
                    for name, domains in domain_mapping.items()
                    if domains is not None
                    for domain in domains + ["missing(NA)"]]
    if len(encoded_cols) > len(set(encoded_cols)):
        duplicates = encoded_cols[:]
        for x in set(encoded_cols):
            duplicates.remove(x)
        warnings.warn("Ambiguous encoding of the column x category pairs: {}".format(set(duplicates)))

    varimp_to_col = {"{}.{}".format(name, domain): name
                     for name, domains in domain_mapping.items()
                     if domains is not None
                     for domain in domains + ["missing(NA)"]
                     }
    for feature in to_process.keys():
        if feature in varimp_to_col:
            column = varimp_to_col[feature]
            consolidated_varimps[column] = consolidated_varimps.get(column, 0) + to_process[feature]
        else:
            raise RuntimeError("Cannot find feature {}".format(feature))

    total_value = sum(consolidated_varimps.values())

    if total_value != 1:
        consolidated_varimps = {k: v / total_value for k, v in consolidated_varimps.items()}

    for col in x:
        if col not in consolidated_varimps:
            consolidated_varimps[col] = 0

    return consolidated_varimps


# This plot is meant to be used only in the explain module.
# It provides the same capabilities as `model.varimp_plot` but without
# either forcing "Agg" backend or showing the plot.
# It also mimics the look and feel of the rest of the explain plots.
def _varimp_plot(model, figsize, num_of_features=None, save_plot_path=None):
    # type: (h2o.model.ModelBase, Tuple[Float, Float], Optional[int]) -> matplotlib.pyplot.Figure
    """
    Variable importance plot.
    :param model: H2O model
    :param figsize: Figure size
    :param num_of_features: Maximum number of variables to plot. Defaults to 10.
    :param save_plot_path: a path to save the plot via using matplotlib function savefig
    :return: object that contains the resulting figure (can be accessed using result.figure())
    """
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
    importances = model.varimp(use_pandas=False)
    feature_labels = [tup[0] for tup in importances]
    val = [tup[2] for tup in importances]
    pos = range(len(feature_labels))[::-1]
    if num_of_features is None:
        num_of_features = min(len(val), 10)

    plt.figure(figsize=figsize)
    plt.barh(pos[0:num_of_features], val[0:num_of_features], align="center",
             height=0.8, color="#1F77B4", edgecolor="none")
    plt.yticks(pos[0:num_of_features], feature_labels[0:num_of_features])
    plt.ylim([min(pos[0:num_of_features]) - 1, max(pos[0:num_of_features]) + 1])
    plt.title("Variable Importance for \"{}\"".format(model.model_id))
    plt.xlabel("Variable Importance")
    plt.ylabel("Variable")
    plt.grid()
    plt.gca().set_axisbelow(True)
    plt.tight_layout()
    fig = plt.gcf()
    if save_plot_path is not None:
        plt.savefig(fname=save_plot_path)
    return decorate_plot_result(figure=fig)


def _interpretable(model):
    # type: (Union[str, h2o.model.ModelBase]) -> bool
    """
    Returns True if model_id is easily interpretable.
    :param model: model or a string containing a model_id
    :returns: bool
    """
    return _get_algorithm(model) in ["glm", "gam", "rulefit"]


def _flatten_list(items):
    # type: (list) -> Generator[Any, None, None]
    """
    Flatten nested lists.
    :param items: a list potentionally containing other lists
    :returns: flattened list
    """
    for x in items:
        if isinstance(x, list):
            for xx in _flatten_list(x):
                yield xx
        else:
            yield x


def _calculate_clustering_indices(matrix):
    # type: (np.ndarray) -> list
    """
    Get a hierarchical clustering leaves order calculated from the clustering of columns.
    :param matrix: numpy.ndarray
    :returns: list of indices of columns
    """
    cols = matrix.shape[1]
    dist = np.zeros((cols, cols))

    for x in range(cols):
        for y in range(cols):
            if x < y:
                dist[x, y] = np.sum(np.power(matrix[:, x] - matrix[:, y], 2))
                dist[y, x] = dist[x, y]
            elif x == y:
                dist[x, x] = float("inf")

    indices = [[i] for i in range(cols)]
    for i in range(cols - 1):
        idx = np.argmin(dist)
        x = idx % cols
        y = idx // cols
        assert x != y
        indices[x].append(indices[y])
        indices[y] = []
        dist[x, :] = np.min(dist[[x, y], :], axis=0)
        dist[y, :] = float("inf")
        dist[:, y] = float("inf")
        dist[x, x] = float("inf")
    result = list(_flatten_list(indices))
    assert len(result) == cols
    return result


def varimp_heatmap(
        models,  # type: Union[h2o.automl._base.H2OAutoMLBaseMixin, h2o.H2OFrame, List[h2o.model.ModelBase]]
        top_n=None,  # type: Option[int]
        num_of_features=20,  # type: Optional[int]
        figsize=(16, 9),  # type: Tuple[float]
        cluster=True,  # type: bool
        colormap="RdYlBu_r",  # type: str
        save_plot_path=None # type: str
):
    # type: (...) -> plt.Figure
    """
    Variable Importance Heatmap across a group of models

    Variable importance heatmap shows variable importance across multiple models.
    Some models in H2O return variable importance for one-hot (binary indicator)
    encoded versions of categorical columns (e.g. Deep Learning, XGBoost).  In order
    for the variable importance of categorical columns to be compared across all model
    types we compute a summarization of the the variable importance across all one-hot
    encoded features and return a single variable importance for the original categorical
    feature. By default, the models and variables are ordered by their similarity.

    :param models: a list of H2O models, an H2O AutoML instance, or an H2OFrame with a 'model_id' column (e.g. H2OAutoML leaderboard)
    :param top_n: DEPRECATED. use just top n models (applies only when used with H2OAutoML)
    :param num_of_features: limit the number of features to plot based on the maximum variable
                            importance across the models. Use None for unlimited.
    :param figsize: figsize: figure size; passed directly to matplotlib
    :param cluster: if True, cluster the models and variables
    :param colormap: colormap to use
    :param save_plot_path: a path to save the plot via using matplotlib function savefig
    :returns: object that contains the resulting figure (can be accessed using ``result.figure()``)

    :examples:
    
    >>> import h2o
    >>> from h2o.automl import H2OAutoML
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train an H2OAutoML
    >>> aml = H2OAutoML(max_models=10)
    >>> aml.train(y=response, training_frame=train)
    >>>
    >>> # Create the variable importance heatmap
    >>> aml.varimp_heatmap()
    """
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
    if isinstance(models, h2o.automl._base.H2OAutoMLBaseMixin):
        models = _check_deprecated_top_n_argument(models, top_n)
    varimps, model_ids,  x = varimp(models=models, cluster=cluster, num_of_features=num_of_features, use_pandas=False)

    plt.figure(figsize=figsize)
    plt.imshow(varimps, cmap=plt.get_cmap(colormap))
    plt.xticks(range(len(model_ids)), model_ids,
               rotation=45, rotation_mode="anchor", ha="right")
    plt.yticks(range(len(x)), x)
    plt.colorbar()
    plt.xlabel("Model Id")
    plt.ylabel("Feature")
    plt.title("Variable Importance Heatmap")
    plt.grid(False)
    fig = plt.gcf()
    if save_plot_path is not None:
        plt.savefig(fname=save_plot_path)
    return decorate_plot_result(figure=fig)


def varimp(
        models,  # type: Union[h2o.automl._base.H2OAutoMLBaseMixin, h2o.H2OFrame, List[h2o.model.ModelBase]]
        num_of_features=20,  # type: Optional[int]
        cluster=True,  # type: bool
        use_pandas=True  # type: bool
):
    # type: (...) -> Union[pandas.DataFrame, Tuple[numpy.ndarray, List[str], List[str]]]
    """
        Get data that are used to build varimp_heatmap plot.

        :param models: a list of H2O models, an H2O AutoML instance, or an H2OFrame with a 'model_id' column (e.g. H2OAutoML leaderboard)
        :param cluster: if True, cluster the models and variables
        :param use_pandas: if True, try to return pandas DataFrame. Otherwise return a triple (varimps, model_ids, variable_names)
        :returns: either pandas DataFrame (if use_pandas == True) or a triple (varimps, model_ids, variable_names)
    """
    if _is_automl_or_leaderboard(models):
        models = list(_get_models_from_automl_or_leaderboard(models, filter_=_has_varimp))
    else:
        # Filter out models that don't have varimp
        models = [model for model in models if _has_varimp(model)]
    if len(models) == 0:
        raise RuntimeError("No model with variable importance")
    varimps = [_consolidate_varimps(model) for model in models]
    x, y = _get_xy(models[0])
    varimps = np.array([[varimp[col] for col in x] for varimp in varimps])

    if num_of_features is not None:
        feature_ranks = np.amax(varimps, axis=0).argsort()
        feature_mask = (feature_ranks.max() - feature_ranks) < num_of_features
        varimps = varimps[:, feature_mask]
        x = [col for i, col in enumerate(x) if feature_mask[i]]

    if cluster and len(models) > 2:
        order = _calculate_clustering_indices(varimps)
        x = [x[i] for i in order]
        varimps = varimps[:, order]
        varimps = varimps.transpose()
        order = _calculate_clustering_indices(varimps)
        models = [models[i] for i in order]
        varimps = varimps[:, order]
    else:
        varimps = varimps.transpose()

    model_ids = _shorten_model_ids([model.model_id for model in models])
    if use_pandas:
        import pandas
        return pandas.DataFrame(varimps, columns=model_ids, index=x)

    return varimps, model_ids, x


def model_correlation_heatmap(
        models,  # type: Union[h2o.automl._base.H2OAutoMLBaseMixin, h2o.H2OFrame, List[h2o.model.ModelBase]]
        frame,  # type: h2o.H2OFrame
        top_n=None,  # type: Optional[int]
        cluster_models=True,  # type: bool
        triangular=True,  # type: bool
        figsize=(13, 13),  # type: Tuple[float]
        colormap="RdYlBu_r",  # type: str
        save_plot_path=None # type: str
):
    # type: (...) -> plt.Figure
    """
    Model Prediction Correlation Heatmap

    This plot shows the correlation between the predictions of the models.
    For classification, frequency of identical predictions is used. By default, models
    are ordered by their similarity (as computed by hierarchical clustering).

    :param models: a list of H2O models, an H2O AutoML instance, or an H2OFrame with a 'model_id' column (e.g. H2OAutoML leaderboard)
    :param frame: H2OFrame
    :param top_n: DEPRECATED. show just top n models (applies only when used with H2OAutoML).
    :param cluster_models: if True, cluster the models
    :param triangular: make the heatmap triangular
    :param figsize: figsize: figure size; passed directly to matplotlib
    :param colormap: colormap to use
    :param save_plot_path: a path to save the plot via using matplotlib function savefig
    :returns: object that contains the resulting figure (can be accessed using ``result.figure()``)

    :examples:
    
    >>> import h2o
    >>> from h2o.automl import H2OAutoML
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train an H2OAutoML
    >>> aml = H2OAutoML(max_models=10)
    >>> aml.train(y=response, training_frame=train)
    >>>
    >>> # Create the model correlation heatmap
    >>> aml.model_correlation_heatmap(test)
    """
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
    if isinstance(models, h2o.automl._base.H2OAutoMLBaseMixin):
        models = _check_deprecated_top_n_argument(models, top_n)
    corr, model_ids = model_correlation(models, frame, cluster_models, use_pandas=False)

    if triangular:
        corr = np.where(np.triu(np.ones_like(corr), k=1).astype(bool), float("nan"), corr)

    plt.figure(figsize=figsize)
    plt.imshow(corr, cmap=plt.get_cmap(colormap), clim=(0.5, 1))
    plt.xticks(range(len(model_ids)), model_ids, rotation=45, rotation_mode="anchor", ha="right")
    plt.yticks(range(len(model_ids)), model_ids)
    plt.colorbar()
    plt.title("Model Correlation")
    plt.xlabel("Model Id")
    plt.ylabel("Model Id")
    plt.grid(False)
    for t in plt.gca().xaxis.get_ticklabels():
        if _interpretable(t.get_text()):
            t.set_color("red")
    for t in plt.gca().yaxis.get_ticklabels():
        if _interpretable(t.get_text()):
            t.set_color("red")
    fig = plt.gcf()
    if save_plot_path is not None:
        plt.savefig(fname=save_plot_path)
    return decorate_plot_result(figure=fig)


def _check_deprecated_top_n_argument(models, top_n):
    if top_n is not None:
        import warnings
        from h2o.exceptions import H2ODeprecationWarning
        warnings.warn("Setting the `top_n` parameter is deprecated, use a leaderboard (sub)frame "
                      "instead, e.g., aml.leaderboard.head({}).".format(top_n), category=H2ODeprecationWarning)
        models = models.leaderboard.head(top_n)
    else:
        models = models.leaderboard.head(20)
    return models


def model_correlation(
        models,  # type: Union[h2o.automl._base.H2OAutoMLBaseMixin, h2o.H2OFrame, List[h2o.model.ModelBase]]
        frame,  # type: h2o.H2OFrame
        cluster_models=True,  # type: bool
        use_pandas=True  # type: bool
):
    # type: (...) -> Union[pandas.DataFrame, Tuple[numpy.ndarray, List[str]]]
    """
    Get data that are used to build model_correlation_heatmap plot.

    :param models: a list of H2O models, an H2O AutoML instance, or an H2OFrame with a 'model_id' column (e.g. H2OAutoML leaderboard)
    :param frame: H2OFrame
    :param cluster_models: if True, cluster the models
    :param use_pandas: if True, try to return pandas DataFrame. Otherwise return a tuple (correlation_matrix, model_ids)
    :returns: either pandas DataFrame (if use_pandas == True) or a tuple (correlation_matrix, model_ids)
    """
    if _is_automl_or_leaderboard(models):
        models = list(_get_models_from_automl_or_leaderboard(models))
    is_classification = frame[models[0].actual_params["response_column"]].isfactor()[0]
    predictions = []
    with no_progress_block():
        for idx, model in enumerate(models):
            predictions.append(model.predict(frame)["predict"])

    if is_classification:
        corr = np.zeros((len(models), len(models)))
        for i in range(len(models)):
            for j in range(len(models)):
                if i <= j:
                    corr[i, j] = (predictions[i] == predictions[j]).mean()[0]
                    corr[j, i] = corr[i, j]
    else:
        corr = np.genfromtxt(StringIO(predictions[0].cbind(predictions[1:]).cor().get_frame_data()),
                             delimiter=",", missing_values="", skip_header=True)
    if cluster_models:
        order = _calculate_clustering_indices(corr)
        corr = corr[order, :]
        corr = corr[:, order]
        models = [models[i] for i in order]

    model_ids = _shorten_model_ids([model.model_id for model in models])

    if use_pandas:
        import pandas
        return pandas.DataFrame(corr, columns=model_ids, index=model_ids)

    return corr, model_ids


def residual_analysis_plot(
        model,  # type: h2o.model.ModelBase
        frame,  # type: h2o.H2OFrame
        figsize=(16, 9),  # type: Tuple[float]
        save_plot_path=None # type: Optional[str]
):
    # type: (...) -> plt.Figure
    """
    Residual Analysis.

    Do Residual Analysis and plot the fitted values vs residuals on a test dataset.
    Ideally, residuals should be randomly distributed. Patterns in this plot can indicate
    potential problems with the model selection (e.g. using simpler model than necessary,
    not accounting for heteroscedasticity, autocorrelation, etc.).  If you notice "striped"
    lines of residuals, that is just an indication that your response variable was integer-valued
    instead of real-valued.

    :param model: H2OModel.
    :param frame: H2OFrame.
    :param figsize: figure size; passed directly to matplotlib.
    :param save_plot_path: a path to save the plot via using matplotlib function savefig.
    :returns: object that contains the resulting matplotlib figure (can be accessed using ``result.figure()``).

    :examples:
    
    >>> import h2o
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train a GBM
    >>> gbm = H2OGradientBoostingEstimator()
    >>> gbm.train(y=response, training_frame=train)
    >>>
    >>> # Create the residual analysis plot
    >>> gbm.residual_analysis_plot(test)
    """
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
    _, y = _get_xy(model)

    with no_progress_block():
        predicted = NumpyFrame(model.predict(frame)["predict"])
    actual = NumpyFrame(frame[y])

    residuals = actual[y] - predicted["predict"]

    plt.figure(figsize=figsize)
    plt.axhline(y=0, c="k")
    plt.scatter(predicted["predict"], residuals)
    plt.grid(True)
    plt.xlabel("Fitted")
    plt.ylabel("Residuals")
    plt.title("Residual Analysis for \"{}\"".format(model.model_id))

    # Rugs
    xlims = plt.xlim()
    ylims = plt.ylim()
    plt.plot([xlims[0] for _ in range(frame.nrow)], residuals,
             "_", color="k", alpha=0.2, ms=20)
    plt.plot(predicted.get("predict"),
             [ylims[0] for _ in range(frame.nrow)],
             "|", color="k", alpha=0.2, ms=20)

    # Fit line
    X = np.vstack([predicted["predict"], np.ones(frame.nrow)]).T
    slope, const = np.linalg.lstsq(X, residuals, rcond=-1)[0]
    plt.plot(xlims, [xlims[0] * slope + const, xlims[1] * slope + const], c="b")

    plt.xlim(xlims)
    plt.ylim(ylims)

    plt.tight_layout()
    fig = plt.gcf()
    if save_plot_path is not None:
        plt.savefig(fname=save_plot_path)
    return decorate_plot_result(figure=fig)


def learning_curve_plot(
        model,  # type: h2o.model.ModelBase
        metric="AUTO",  # type: str
        cv_ribbon=None,  # type: Optional[bool]
        cv_lines=None,  # type: Optional[bool]
        figsize=(16,9),  # type: Tuple[float]
        colormap=None,  # type: Optional[str]
        save_plot_path=None # type: Optional[str]
):
    # type: (...) -> plt.Figure
    """
    Learning curve plot.

    Create the learning curve plot for an H2O Model. Learning curves show the error metric dependence on
    learning progress (e.g. RMSE vs number of trees trained so far in GBM). There can be up to 4 curves
    showing Training, Validation, Training on CV Models, and Cross-validation error.

    :param model: an H2O model.
    :param metric: a stopping metric.
    :param cv_ribbon: if ``True``, plot the CV mean and CV standard deviation as a ribbon around the mean;
                      if None, it will attempt to automatically determine if this is suitable visualization.
    :param cv_lines: if ``True``, plot scoring history for individual CV models; if None, it will attempt to
                     automatically determine if this is suitable visualization.
    :param figsize: figure size; passed directly to matplotlib.
    :param colormap: colormap to use.
    :param save_plot_path: a path to save the plot via using matplotlib function savefig.
    :return: object that contains the resulting figure (can be accessed using ``result.figure()``).

    :examples:
    
    >>> import h2o
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train a GBM
    >>> gbm = H2OGradientBoostingEstimator()
    >>> gbm.train(y=response, training_frame=train)
    >>>
    >>> # Create the learning curve plot
    >>> gbm.learning_curve_plot()
    """
    if model.algo == "stackedensemble":
        model = model.metalearner()

    if model.algo not in ("stackedensemble", "glm", "gam", "glrm", "deeplearning",
                          "drf", "gbm", "xgboost", "coxph", "isolationforest"):
        raise H2OValueError("Algorithm {} doesn't support learning curve plot!".format(model.algo))

    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)

    metric_mapping = {'anomaly_score': 'mean_anomaly_score',
                      'custom': 'custom',
                      'custom_increasing': 'custom',
                      'deviance': 'deviance',
                      'logloss': 'logloss',
                      'rmse': 'rmse',
                      'mae': 'mae',
                      'auc': 'auc',
                      'aucpr': 'pr_auc',
                      'lift_top_group': 'lift',
                      'misclassification': 'classification_error',
                      'objective': 'objective',
                      'convergence': 'convergence',
                      'negative_log_likelihood': 'negative_log_likelihood',
                      'sumetaieta02': 'sumetaieta02'}
    inverse_metric_mappping = {v: k for k, v in metric_mapping.items()}
    inverse_metric_mappping["custom"] = "custom, custom_increasing"

    # Using the value from output to keep it simple - only one version required - (no need to use pandas for small data)
    scoring_history = model._model_json["output"]["scoring_history"] or model._model_json["output"].get("glm_scoring_history")
    if scoring_history is None:
        raise RuntimeError("Could not retrieve scoring history for {}".format(model.algo))
    scoring_history = _preprocess_scoring_history(model, scoring_history)

    allowed_metrics = []
    allowed_timesteps = []
    if model.algo in ("glm", "gam"):
        if model.actual_params["lambda_search"]:
            import h2o.two_dim_table
            allowed_timesteps = ["iteration"]
        elif model.actual_params.get("HGLM"):
            allowed_timesteps = ["iterations", "duration"]
        else:
            allowed_timesteps = ["iterations", "duration"]

        allowed_metrics = ["deviance", "objective", "negative_log_likelihood", "convergence", "sumetaieta02",
                           "logloss", "auc", "classification_error", "rmse", "lift", "pr_auc", "mae"]
        allowed_metrics = [m for m in allowed_metrics
                           if m in scoring_history.col_header or
                           "training_{}".format(m) in scoring_history.col_header or
                           "{}_train".format(m) in scoring_history.col_header]
    elif model.algo == "glrm":
        allowed_metrics = ["objective"]
        allowed_timesteps = ["iterations"]
    elif model.algo in ("deeplearning", "drf", "gbm", "xgboost"):
        model_category = model._model_json["output"]["model_category"]
        if "Binomial" == model_category:
            allowed_metrics = ["logloss", "auc", "classification_error", "rmse", "lift", "pr_auc"]
        elif model_category in ["Multinomial", "Ordinal"]:
            allowed_metrics = ["logloss", "classification_error", "rmse", "pr_auc", "auc"]
        elif "Regression" == model_category:
            allowed_metrics = ["rmse", "deviance", "mae"]
        if model.algo in ["drf", "gbm"]:
            allowed_metrics += ["custom"]
    elif model.algo == "coxph":
        allowed_metrics = ["loglik"]
        allowed_timesteps = ["iterations"]
    elif model.algo == "isolationforest":
        allowed_timesteps = ["number_of_trees"]
        allowed_metrics = ["mean_anomaly_score"]

    if model.algo == "deeplearning":
        allowed_timesteps = ["epochs", "iterations", "samples"]
    elif model.algo in ["drf", "gbm", "xgboost"]:
        allowed_timesteps = ["number_of_trees"]

    if metric.lower() == "auto":
        metric = allowed_metrics[0]
    else:
        metric = metric_mapping.get(metric.lower())

    if metric not in allowed_metrics:
        raise H2OValueError("for {}, metric must be one of: {}".format(
            model.algo.upper(),
            ", ".join(inverse_metric_mappping[m.lower()] for m in allowed_metrics)
        ))

    timestep = allowed_timesteps[0]

    if ("deviance" == metric
            and model.algo in ["glm", "gam"]
            and not model.actual_params.get("HGLM", False)
            and "deviance_train" in scoring_history.col_header):
        training_metric = "deviance_train"
        validation_metric = "deviance_test"
    elif metric in ("objective", "convergence", "loglik", "mean_anomaly_score"):
        training_metric = metric
        validation_metric = "UNDEFINED"
    else:
        training_metric = "training_{}".format(metric)
        validation_metric = "validation_{}".format(metric)

    selected_timestep_value = None
    if "number_of_trees" == timestep:
        selected_timestep_value = model.actual_params["ntrees"]
    elif timestep in ["iteration", "iterations"]:
        if "coxph" == model.algo:
            selected_timestep_value = model._model_json["output"]["iter"]
        else:
            selected_timestep_value = model.summary()["number_of_iterations"][0]
    elif "epochs" == timestep:
        selected_timestep_value = model.actual_params["epochs"]

    if colormap is None:
        col_train, col_valid = "#785ff0", "#ff6000"
        col_cv_train, col_cv_valid = "#648fff", "#ffb000"
    else:
        col_train, col_valid, col_cv_train, col_cv_valid = plt.get_cmap(colormap, 4)(list(range(4)))

    # Get scoring history with only filled in entries
    scoring_history = _preprocess_scoring_history(model, scoring_history, training_metric)

    plt.figure(figsize=figsize)
    plt.grid(True)

    if model._model_json["output"].get("cv_scoring_history"):
        if cv_ribbon or cv_ribbon is None:
            cvsh_train = defaultdict(list)
            cvsh_valid = defaultdict(list)
            for cvsh in model._model_json["output"]["cv_scoring_history"]:
                cvsh = _preprocess_scoring_history(model, cvsh, training_metric)
                for i in range(len(cvsh[timestep])):
                    cvsh_train[cvsh[timestep][i]].append(cvsh[training_metric][i])
                if validation_metric in cvsh.col_header:
                    for i in range(len(cvsh[timestep])):
                        cvsh_valid[cvsh[timestep][i]].append(cvsh[validation_metric][i])
            mean_train = np.array(sorted(
                [(k, np.mean(v)) for k, v in cvsh_train.items()],
                key=lambda k: k[0]
            ))
            sd_train = np.array(sorted(
                [(k, np.std(v)) for k, v in cvsh_train.items()],
                key=lambda k: k[0]
            ))[:, 1]

            len_train = np.array(sorted(
                [(k, len(v)) for k, v in cvsh_train.items()],
                key=lambda k: k[0]
            ))[:, 1]
            if len(len_train) > 1 and (cv_ribbon or len_train.mean() > 2 and np.mean(len_train[:-1] == len_train[1:]) >= 0.5):
                plt.plot(mean_train[:, 0], mean_train[:, 1], c=col_cv_train,
                         label="Training (CV Models)")
                plt.fill_between(mean_train[:, 0],
                                 mean_train[:, 1] - sd_train,
                                 mean_train[:, 1] + sd_train,
                                 color=col_cv_train, alpha=0.25)
                if len(cvsh_valid) > 0:
                    mean_valid = np.array(sorted(
                        [(k, np.mean(v)) for k, v in cvsh_valid.items()],
                        key=lambda k: k[0]
                    ))
                    sd_valid = np.array(sorted(
                        [(k, np.std(v)) for k, v in cvsh_valid.items()],
                        key=lambda k: k[0]
                    ))[:, 1]
                    plt.plot(mean_valid[:, 0], mean_valid[:, 1], c=col_cv_valid,
                             label="Cross-validation")
                    plt.fill_between(mean_valid[:, 0],
                                     mean_valid[:, 1] - sd_valid,
                                     mean_valid[:, 1] + sd_valid,
                                     color=col_cv_valid, alpha=0.25)
            else:
                cv_lines = cv_lines is None or cv_lines

        if cv_lines:
            for cvsh in model._model_json["output"]["cv_scoring_history"]:
                cvsh = _preprocess_scoring_history(model, cvsh, training_metric)
                plt.plot(cvsh[timestep],
                         cvsh[training_metric],
                         label="Training (CV Models)",
                         c=col_cv_train,
                         linestyle="dotted")
                if validation_metric in cvsh.col_header:
                    plt.plot(cvsh[timestep],
                             cvsh[validation_metric],
                             label="Cross-validation",
                             c=col_cv_valid,
                             linestyle="dotted"
                             )

    plt.plot(scoring_history[timestep],
             scoring_history[training_metric],
             "o-",
             label="Training",
             c=col_train)
    if validation_metric in scoring_history.col_header:
        plt.plot(scoring_history[timestep],
                 scoring_history[validation_metric],
                 "o-",
                 label="Validation",
                 c=col_valid)

    if selected_timestep_value is not None:
        plt.axvline(x=selected_timestep_value, label="Selected\n{}".format(timestep), c="#2FBB24")

    plt.title("Learning Curve\nfor {}".format(_shorten_model_ids([model.model_id])[0]))
    plt.xlabel(timestep)
    plt.ylabel(metric)
    handles, labels = plt.gca().get_legend_handles_labels()
    labels_and_handles = dict(zip(labels, handles))
    labels_and_handles_ordered = OrderedDict()
    for lbl in ["Training", "Training (CV Models)", "Validation", "Cross-validation", "Selected\n{}".format(timestep)]:
        if lbl in labels_and_handles:
            labels_and_handles_ordered[lbl] = labels_and_handles[lbl]
    plt.legend(list(labels_and_handles_ordered.values()), list(labels_and_handles_ordered.keys()))

    if save_plot_path is not None:
        plt.savefig(fname=save_plot_path)

    return decorate_plot_result(figure=plt.gcf())


def _calculate_pareto_front(x, y, top=True, left=True):
    # Make sure we are not given something unexpected like pandas which has separate indexes and causes unintuitive results
    assert isinstance(x, np.ndarray)
    assert isinstance(y, np.ndarray)

    cumagg = np.maximum.accumulate if top else np.minimum.accumulate
    if not left:
        x = -x

    order = np.argsort(-y if top else y)
    order = order[np.argsort(x[order], kind="stable")]

    return order[np.unique(cumagg(y[order]), return_index=True)[1]]


def _pretty_metric_name(metric):
    return dict(
        auc="Area Under ROC Curve",
        aucpr="Area Under Precision/Recall Curve",
        logloss="Logloss",
        mae="Mean Absolute Error",
        mean_per_class_error="Mean Per Class Error",
        mean_residual_deviance="Mean Residual Deviance",
        mse="Mean Square Error",
        predict_time_per_row_ms="Per-Row Prediction Time [ms]",
        rmse="Root Mean Square Error",
        rmsle="Root Mean Square Log Error",
        training_time_ms="Training Time [ms]"
    ).get(metric, metric)


def pareto_front(frame,  # type: H2OFrame
                 x_metric=None,  # type: Optional[str]
                 y_metric=None,  # type: Optional[str]
                 optimum="top left",  # type: Literal["top left", "top right", "bottom left", "bottom right"]
                 title=None,  # type: Optional[str]
                 color_col="algo",  # type: str
                 figsize=(16, 9),  # type: Union[Tuple[float], List[float]]
                 colormap="Dark2"  # type: str
                 ):
    """
    Create Pareto front and plot it. Pareto front contains models that are optimal in a sense that for each model in the
    Pareto front there isn't a model that would be better in both criteria. For example, this can be useful in picking
    models that are fast to predict and at the same time have high accuracy. For generic data.frames/H2OFrames input
    the task is assumed to be minimization for both metrics.

    :param frame: an H2OFrame
    :param x_metric: metric present in the leaderboard
    :param y_metric: metric present in the leaderboard
    :param optimum: location of the optimum in XY plane
    :param title: title used for the plot
    :param color_col: categorical column in the leaderboard that should be used for coloring the points
    :param figsize: figure size; passed directly to matplotlib
    :param colormap: colormap to use
    :return: object that contains the resulting figure (can be accessed using ``result.figure()``)

    :examples:
    
    >>> import h2o
    >>> from h2o.automl import H2OAutoML
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> from h2o.grid import H2OGridSearch
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train an H2OAutoML
    >>> aml = H2OAutoML(max_models=10)
    >>> aml.train(y=response, training_frame=train)
    >>>
    >>> gbm_params1 = {'learn_rate': [0.01, 0.1],
    >>>                'max_depth': [3, 5, 9]}
    >>> grid = H2OGridSearch(model=H2OGradientBoostingEstimator,
    >>>                      hyper_params=gbm_params1)
    >>> grid.train(y=response, training_frame=train)
    >>>
    >>> combined_leaderboard = h2o.make_leaderboard([aml, grid], test, extra_columns="ALL")
    >>>
    >>> # Create the Pareto front
    >>> pf = h2o.explanation.pareto_front(combined_leaderboard, "predict_time_per_row_ms", "rmse", optimum="bottom left")
    >>> pf.figure() # get the Pareto front plot
    >>> pf # H2OFrame containing the Pareto front subset of the leaderboard
    """
    plt = get_matplotlib_pyplot(False, True)
    from matplotlib.lines import Line2D
    if isinstance(frame, h2o.H2OFrame):
        leaderboard = frame
    else:
        try:  # Maybe it's pandas or other datastructure coercible to H2OFrame
            leaderboard = h2o.H2OFrame(frame)
        except Exception:
            raise ValueError("`frame` parameter has to be either H2OAutoML, H2OGrid, list of models or coercible to H2OFrame!")

    if x_metric not in leaderboard.names:
        raise ValueError("x_metric {} is not in the leaderboard!".format(x_metric))
    if y_metric not in leaderboard.names:
        raise ValueError("y_metric {} is not in the leaderboard!".format(y_metric))

    assert optimum.lower() in ("top left", "top right", "bottom left", "bottom right"), "Optimum has to be one of \"top left\", \"top right\", \"bottom left\", \"bottom right\"."
    top = "top" in optimum.lower()
    left = "left" in optimum.lower()

    nf = NumpyFrame(leaderboard[[x_metric, y_metric]])

    x = nf[x_metric]
    y = nf[y_metric]

    pf = _calculate_pareto_front(x, y, top=top, left=left)

    cols = None
    fig = plt.figure(figsize=figsize)
    if color_col in leaderboard.columns:
        color_col_vals = np.array(leaderboard[color_col].as_data_frame(use_pandas=False, header=False)).reshape(-1)
        colors = plt.get_cmap(colormap, len(set(color_col_vals)))(list(range(len(set(color_col_vals)))))
        color_col_to_color = dict(zip(set(color_col_vals), colors))
        cols = np.array([color_col_to_color[a] for a in color_col_vals])
        plt.legend(handles=[Line2D([0], [0], marker='o', color="w", label=a, markerfacecolor=color_col_to_color[a], markersize=10)
                            for a in color_col_to_color.keys()])
    plt.scatter(x, y, c=cols, alpha=0.5)
    plt.plot(x[pf], y[pf], c="k")
    plt.scatter(x[pf], y[pf], c=cols[pf] if cols is not None else None, s=100, zorder=100)
    plt.xlabel(_pretty_metric_name(x_metric))
    plt.ylabel(_pretty_metric_name(y_metric))
    plt.grid(True)

    if title is not None:
        plt.title(title)
    else:
        plt.title("Pareto Front")

    leaderboard_pareto_subset = leaderboard[sorted(list(pf)), :]

    return decorate_plot_result(res=leaderboard_pareto_subset, figure=fig)


def _preprocess_scoring_history(model, scoring_history, training_metric=None):
    empty_columns = [all(row[col_idx] == "" for row in scoring_history.cell_values)
                     for col_idx in range(len(scoring_history.col_header))]

    scoring_history = h2o.two_dim_table.H2OTwoDimTable(
        table_header=scoring_history._table_header,
        table_description=scoring_history._table_description,
        col_header=[ch for i, ch in enumerate(scoring_history.col_header) if not empty_columns[i]],
        col_types=[ct for i, ct in enumerate(scoring_history.col_types) if not empty_columns[i]],
        cell_values=[[v for i, v in enumerate(vals) if not empty_columns[i]]
                     for vals in scoring_history.cell_values])

    if model.algo in ("glm", "gam") and model.actual_params["lambda_search"]:
        alpha_best = model._model_json["output"]["alpha_best"]
        alpha_idx = scoring_history.col_header.index("alpha")
        iteration_idx = scoring_history.col_header.index("iteration")
        scoring_history = h2o.two_dim_table.H2OTwoDimTable(
            table_header=scoring_history._table_header,
            table_description=scoring_history._table_description,
            col_header=scoring_history.col_header,
            col_types=scoring_history.col_types,
            cell_values=sorted([list(v) for v in scoring_history.cell_values if v[alpha_idx] == alpha_best],
                               key=lambda row: row[iteration_idx]))

    if training_metric is not None:
        # Remove empty metric values, e.g., from GLM when score_each_iteration = False
        training_metric_idx = scoring_history.col_header.index(training_metric)
        scoring_history = h2o.two_dim_table.H2OTwoDimTable(
            table_header=scoring_history._table_header,
            table_description=scoring_history._table_description,
            col_header=scoring_history.col_header,
            col_types=scoring_history.col_types,
            cell_values=[list(v) for v in scoring_history.cell_values if v[training_metric_idx] != ""])

    return scoring_history


def _is_tree_model(model):
    # type: (Union[str, h2o.model.ModelBase]) -> bool
    """
    Is the model a tree model id?
    :param model: model or a string containing a model_id
    :returns: bool
    """
    return _get_algorithm(model) in ["drf", "gbm", "xgboost"]


def _get_tree_models(
        models,  # type: Union[h2o.H2OFrame, List[h2o.model.ModelBase]]
        top_n=float("inf")  # type: Union[float, int]
):
    # type: (...) -> List[h2o.model.ModelBase]
    """
    Get list of top_n tree models.

    :param models: either H2OAutoML object or list of H2O Models
    :param top_n: maximum number of tree models to return
    :returns: list of tree models
    """
    if _is_automl_or_leaderboard(models):
        model_ids = _get_model_ids_from_automl_or_leaderboard(models, filter_=_is_tree_model)
        return [
            h2o.get_model(model_id)
            for model_id in model_ids[:min(top_n, len(model_ids))]
        ]
    elif isinstance(models, h2o.model.ModelBase):
        if _is_tree_model(models):
            return [models]
        else:
            return []
    models = [
        model
        for model in models
        if _is_tree_model(model)
    ]
    return models[:min(len(models), top_n)]


def _get_leaderboard(
        models,  # type: Union[h2o.automl._base.H2OAutoMLBaseMixin, List[h2o.model.ModelBase]]
        frame,  # type: Optional[h2o.H2OFrame]
        row_index=None,  # type: Optional[int]
        top_n=20  # type: Union[float, int]
):
    # type: (...) -> h2o.H2OFrame
    """
    Get leaderboard either from AutoML or list of models.

    :param models: H2OAutoML object or list of models
    :param frame: H2OFrame used for calculating prediction when row_index is specified
    :param row_index: if specified, calculated prediction for the given row
    :param top_n: show just top n models in the leaderboard
    :returns: H2OFrame
    """
    leaderboard = models if isinstance(models, h2o.H2OFrame) else h2o.make_leaderboard(models, frame,
                                                                                       extra_columns="ALL" if frame is not None else None)
    leaderboard = leaderboard.head(rows=min(leaderboard.nrow, top_n))
    if row_index is not None:
        model_ids = [m[0] for m in
                     leaderboard["model_id"].as_data_frame(use_pandas=False, header=False)]
        with no_progress_block():
            preds = h2o.get_model(model_ids[0]).predict(frame[row_index, :])
            for model_id in model_ids[1:]:
                preds = preds.rbind(h2o.get_model(model_id).predict(frame[row_index, :]))

            leaderboard = leaderboard.cbind(preds)
    return leaderboard


def _process_explanation_lists(
        exclude_explanations,  # type: Union[str, List[str]]
        include_explanations,  # type: Union[str, List[str]]
        possible_explanations  # type: List[str]
):
    # type: (...) -> List[str]
    """
    Helper function to process explanation lists.

    :param exclude_explanations: list of model explanations to exclude
    :param include_explanations: list of model explanations to include
    :param possible_explanations: list of all possible explanations
    :returns: list of actual explanations
    """
    if not isinstance(include_explanations, list):
        include_explanations = [include_explanations]
    if not isinstance(exclude_explanations, list):
        exclude_explanations = [exclude_explanations]
    include_explanations = [exp.lower() for exp in include_explanations]
    exclude_explanations = [exp.lower() for exp in exclude_explanations]
    if len(exclude_explanations) == 0:
        explanations = possible_explanations if "all" in include_explanations \
            else include_explanations
    else:
        if "all" not in include_explanations:
            raise RuntimeError(
                "Only one of include_explanations or exclude_explanation should be specified!")
        for exp in exclude_explanations:
            if exp not in possible_explanations:
                raise RuntimeError("Unknown explanation \"{}\". Please use one of: {}".format(
                    exp, possible_explanations))
        explanations = [exp for exp in possible_explanations if exp not in exclude_explanations]
    return explanations


def _process_models_input(
        models,  # type: Union[h2o.automl._base.H2OAutoMLBaseMixin, h2o.H2OFrame, List, h2o.model.ModelBase]
        frame,  # type: h2o.H2OFrame
):
    # type: (...) -> Tuple[bool, List, bool, bool, bool, List, List]
    """
    Helper function to get basic information about models/H2OAutoML.

    :param models: H2OAutoML/List of models/H2O Model
    :param frame: H2O Frame
    :returns: tuple (is_aml, models_to_show, classification, multinomial_classification,
                    multiple_models, targets, tree_models_to_show)
    """
    is_aml = False
    if _is_automl_or_leaderboard(models):
        is_aml = True
        if isinstance(models, h2o.automl._base.H2OAutoMLBaseMixin):
            models_to_show = [models.leader]
            models = models.leaderboard
        else:
            models_to_show = [h2o.get_model(models[0, "model_id"])]
        if _has_varimp(models_to_show[0]):
            models_with_varimp = models_to_show
        else:
            model_with_varimp = next(_get_models_from_automl_or_leaderboard(models, filter_=_has_varimp), None)
            models_with_varimp = [] if model_with_varimp is None else [model_with_varimp]
        multiple_models = models.nrow > 1
    elif isinstance(models, h2o.model.ModelBase):
        models_to_show = [models]
        multiple_models = False
        models_with_varimp = [models] if _has_varimp(models) else []
    else:
        models_to_show = models
        multiple_models = len(models) > 1
        models_with_varimp = [model for model in models if _has_varimp(model)]
    tree_models_to_show = _get_tree_models(models, 1 if is_aml else float("inf"))
    y = _get_xy(models_to_show[0])[1]
    classification = frame[y].isfactor()[0]
    multinomial_classification = classification and frame[y].nlevels()[0] > 2
    targets = [None]
    if multinomial_classification:
        targets = [[t] for t in frame[y].levels()[0]]
    return is_aml, models_to_show, classification, multinomial_classification, \
           multiple_models, targets, tree_models_to_show, models_with_varimp


def _custom_args(user_specified, **kwargs):
    # type: (Optional[Dict], **Any) -> Dict
    """
    Helper function to make customization of arguments easier.

    :param user_specified: dictionary of user specified overrides or None
    :param kwargs: default values, such as, `top_n=5`
    :returns: dictionary of actual arguments to use
    """
    if user_specified is None:
        user_specified = dict()

    result = dict(**kwargs)
    result.update(user_specified)

    return result


def explain(
        models,  # type: Union[h2o.automl._base.H2OAutoMLBaseMixin, h2o.H2OFrame, List[h2o.model.ModelBase]]
        frame,  # type: h2o.H2OFrame
        columns=None,  # type: Optional[Union[List[int], List[str]]]
        top_n_features=5,  # type: int
        include_explanations="ALL",  # type: Union[str, List[str]]
        exclude_explanations=[],  # type: Union[str, List[str]]
        plot_overrides=dict(),  # type: Dict
        figsize=(16, 9),  # type: Tuple[float]
        render=True,  # type: bool
        qualitative_colormap="Dark2",  # type: str
        sequential_colormap="RdYlBu_r"  # type: str
):
    # type: (...) -> H2OExplanation
    """
    Generate model explanations on frame data set.

    The H2O Explainability Interface is a convenient wrapper to a number of explainabilty
    methods and visualizations in H2O.  The function can be applied to a single model or group
    of models and returns an object containing explanations, such as a partial dependence plot
    or a variable importance plot.  Most of the explanations are visual (plots).
    These plots can also be created by individual utility functions/methods as well.

    :param models: a list of H2O models, an H2O AutoML instance, or an H2OFrame with a 'model_id' column (e.g. H2OAutoML leaderboard).
    :param frame: H2OFrame.
    :param columns: either a list of columns or column indices to show. If specified
                    parameter ``top_n_features`` will be ignored.
    :param top_n_features: a number of columns to pick using variable importance (where applicable).
    :param include_explanations: if specified, return only the specified model explanations
                                 (mutually exclusive with ``exclude_explanations``).
    :param exclude_explanations: exclude specified model explanations.
    :param plot_overrides: overrides for individual model explanations.
    :param figsize: figure size; passed directly to matplotlib.
    :param render: if ``True``, render the model explanations; otherwise model explanations are just returned.
    :returns: H2OExplanation containing the model explanations including headers and descriptions.

    :examples:
    
    >>> import h2o
    >>> from h2o.automl import H2OAutoML
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train an H2OAutoML
    >>> aml = H2OAutoML(max_models=10)
    >>> aml.train(y=response, training_frame=train)
    >>>
    >>> # Create the H2OAutoML explanation
    >>> aml.explain(test)
    >>>
    >>> # Create the leader model explanation
    >>> aml.leader.explain(test)
    """
    plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
    (is_aml, models_to_show, classification, multinomial_classification, multiple_models, targets,
     tree_models_to_show, models_with_varimp) = _process_models_input(models, frame)

    if top_n_features < 0:
        top_n_features = float("inf")

    if columns is not None and isinstance(columns, list):
        columns_of_interest = [frame.columns[col] if isinstance(col, int) else col for col in columns]
    else:
        columns_of_interest = None

    possible_explanations = [
        "leaderboard",
        "confusion_matrix",
        "residual_analysis",
        "learning_curve",
        "varimp",
        "varimp_heatmap",
        "model_correlation_heatmap",
        "shap_summary",
        "pdp",
        "ice"
    ]

    explanations = _process_explanation_lists(
        exclude_explanations=exclude_explanations,
        include_explanations=include_explanations,
        possible_explanations=possible_explanations
    )

    if render:
        display = _display
    else:
        display = _dont_display

    result = H2OExplanation()
    if multiple_models and "leaderboard" in explanations:
        result["leaderboard"] = H2OExplanation()
        result["leaderboard"]["header"] = display(Header("Leaderboard"))
        result["leaderboard"]["description"] = display(Description("leaderboard"))
        result["leaderboard"]["data"] = display(_get_leaderboard(models, frame))

    if classification:
        if "confusion_matrix" in explanations:
            result["confusion_matrix"] = H2OExplanation()
            result["confusion_matrix"]["header"] = display(Header("Confusion Matrix"))
            result["confusion_matrix"]["description"] = display(Description("confusion_matrix"))
            result["confusion_matrix"]["subexplanations"] = H2OExplanation()
            for model in models_to_show:
                result["confusion_matrix"]["subexplanations"][model.model_id] = H2OExplanation()
                result["confusion_matrix"]["subexplanations"][model.model_id]["header"] = display(
                    Header(model.model_id, 2))
                result["confusion_matrix"]["subexplanations"][model.model_id]["plots"] = H2OExplanation()
                result["confusion_matrix"]["subexplanations"][model.model_id]["plots"][model.model_id] = display(
                    model.model_performance(
                        **_custom_args(plot_overrides.get("confusion_matrix"), test_data=frame)
                    ).confusion_matrix()
                )
    else:
        if "residual_analysis" in explanations:
            result["residual_analysis"] = H2OExplanation()
            result["residual_analysis"]["header"] = display(Header("Residual Analysis"))
            result["residual_analysis"]["description"] = display(Description("residual_analysis"))
            result["residual_analysis"]["plots"] = H2OExplanation()
            for model in models_to_show:
                result["residual_analysis"]["plots"][model.model_id] = display(
                    residual_analysis_plot(model,
                                           frame,
                                           **_custom_args(
                                               plot_overrides.get(
                                                   "residual_analysis"),
                                               figsize=figsize)))
    if "learning_curve" in explanations:
        result["learning_curve"] = H2OExplanation()
        result["learning_curve"]["header"] = display(Header("Learning Curve Plot"))
        result["learning_curve"]["description"] = display(Description("learning_curve"))
        result["learning_curve"]["plots"] = H2OExplanation()
        for model in models_to_show:
            result["learning_curve"]["plots"][model.model_id] = display(
                model.learning_curve_plot(**_custom_args(plot_overrides.get("learning_curve"), figsize=figsize))
            )
    if len(models_with_varimp) > 0 and "varimp" in explanations:
        result["varimp"] = H2OExplanation()
        result["varimp"]["header"] = display(Header("Variable Importance"))
        result["varimp"]["description"] = display(Description("variable_importance"))
        result["varimp"]["plots"] = H2OExplanation()
        for model in models_with_varimp:
            varimp_plot = _varimp_plot(model, figsize, **plot_overrides.get("varimp_plot", dict()))
            result["varimp"]["plots"][model.model_id] = display(varimp_plot)
        if columns_of_interest is None:
            varimps = _consolidate_varimps(models_with_varimp[0])
            columns_of_interest = sorted(varimps.keys(), key=lambda k: -varimps[k])[
                                  :min(top_n_features, len(varimps))]
    else:
        if columns_of_interest is None:
            columns_of_interest = _get_xy(models_to_show[0])[0]
    # Make sure that there are no string columns to explain as they are not supported by pdp
    # Usually those columns would not be used by algos so this just makes sure to exclude them
    # if user specifies top_n=float('inf') or columns_of_interest=x etc.
    dropped_string_columns = [col for col in columns_of_interest if frame.type(col) == "string"]
    if len(dropped_string_columns) > 0:
        warnings.warn("Dropping string columns as they are not supported: {}".format(dropped_string_columns))
        columns_of_interest = [col for col in columns_of_interest if frame.type(col) != "string"]

    if is_aml or len(models_to_show) > 1:
        if "varimp_heatmap" in explanations:
            result["varimp_heatmap"] = H2OExplanation()
            result["varimp_heatmap"]["header"] = display(
                Header("Variable Importance Heatmap"))
            result["varimp_heatmap"]["description"] = display(
                Description("varimp_heatmap"))
            result["varimp_heatmap"]["plots"] = display(varimp_heatmap(
                models,
                **_custom_args(plot_overrides.get("varimp_heatmap"),
                               colormap=sequential_colormap,
                               figsize=figsize)))

        if "model_correlation_heatmap" in explanations:
            result["model_correlation_heatmap"] = H2OExplanation()
            result["model_correlation_heatmap"]["header"] = display(Header("Model Correlation"))
            result["model_correlation_heatmap"]["description"] = display(Description(
                "model_correlation_heatmap"))
            result["model_correlation_heatmap"]["plots"] = display(model_correlation_heatmap(
                models, **_custom_args(plot_overrides.get("model_correlation_heatmap"),
                                       frame=frame,
                                       colormap=sequential_colormap,
                                       figsize=figsize)))

    # SHAP Summary
    if len(tree_models_to_show) > 0 and not multinomial_classification \
            and "shap_summary" in explanations:
        result["shap_summary"] = H2OExplanation()
        result["shap_summary"]["header"] = display(Header("SHAP Summary"))
        result["shap_summary"]["description"] = display(Description("shap_summary"))
        result["shap_summary"]["plots"] = H2OExplanation()
        for tree_model in tree_models_to_show:
            result["shap_summary"]["plots"][tree_model.model_id] = display(shap_summary_plot(
                tree_model,
                **_custom_args(
                    plot_overrides.get("shap_summary_plot"),
                    frame=frame,
                    figsize=figsize
                )))

    # PDP
    if "pdp" in explanations:
        if is_aml or multiple_models:
            result["pdp"] = H2OExplanation()
            result["pdp"]["header"] = display(Header("Partial Dependence Plots"))
            result["pdp"]["description"] = display(Description("pdp"))
            result["pdp"]["plots"] = H2OExplanation()
            for column in columns_of_interest:
                result["pdp"]["plots"][column] = H2OExplanation()
                for target in targets:
                    pdp = display(pd_multi_plot(
                        models, column=column, target=target,
                        **_custom_args(plot_overrides.get("pdp"),
                                       frame=frame,
                                       figsize=figsize,
                                       colormap=qualitative_colormap)))
                    if target is None:
                        result["pdp"]["plots"][column] = pdp
                    else:
                        result["pdp"]["plots"][column][target[0]] = pdp
        else:
            result["pdp"] = H2OExplanation()
            result["pdp"]["header"] = display(Header("Partial Dependence Plots"))
            result["pdp"]["description"] = display(Description("pdp"))
            result["pdp"]["plots"] = H2OExplanation()
            for column in columns_of_interest:
                result["pdp"]["plots"][column] = H2OExplanation()
                for target in targets:
                    fig = pd_plot(models_to_show[0], column=column, target=target,
                                  **_custom_args(plot_overrides.get("pdp"),
                                                 frame=frame,
                                                 figsize=figsize,
                                                 colormap=qualitative_colormap))
                    if target is None:
                        result["pdp"]["plots"][column] = display(fig)
                    else:
                        result["pdp"]["plots"][column][target[0]] = display(fig)

    # ICE
    if "ice" in explanations and not classification:
        result["ice"] = H2OExplanation()
        result["ice"]["header"] = display(Header("Individual Conditional Expectation"))
        result["ice"]["description"] = display(Description("ice"))
        result["ice"]["plots"] = H2OExplanation()
        for column in columns_of_interest:
            result["ice"]["plots"][column] = H2OExplanation()
            for model in models_to_show:
                result["ice"]["plots"][column][model.model_id] = H2OExplanation()
                for target in targets:
                    ice = display(
                        ice_plot(
                            model, column=column,
                            target=target,
                            **_custom_args(
                                plot_overrides.get("ice_plot"),
                                frame=frame,
                                figsize=figsize,
                                colormap=sequential_colormap
                            )))
                    if target is None:
                        result["ice"]["plots"][column][model.model_id] = ice
                    else:
                        result["ice"]["plots"][column][model.model_id][target[0]] = ice

    return result


def explain_row(
        models,  # type: Union[h2o.automl._base.H2OAutoMLBaseMixin, List[h2o.model.ModelBase]]
        frame,  # type: h2o.H2OFrame
        row_index,  # type: int
        columns=None,  # type: Optional[Union[List[int], List[str]]]
        top_n_features=5,  # type: int
        include_explanations="ALL",  # type: Union[str, List[str]]
        exclude_explanations=[],  # type: Union[str, List[str]]
        plot_overrides=dict(),  # type: Dict
        qualitative_colormap="Dark2",  # type: str
        figsize=(16, 9),  # type: Tuple[float]
        render=True,  # type: bool
):
    # type: (...) -> H2OExplanation
    """
    Generate model explanations on frame data set for a given instance.

    Explain the behavior of a model or group of models with respect to a single row of data.
    The function returns an object containing explanations, such as a partial dependence plot
    or a variable importance plot.  Most of the explanations are visual (plots).
    These plots can also be created by individual utility functions/methods as well.

    :param models: H2OAutoML object, supervised H2O model, or list of supervised H2O models.
    :param frame: H2OFrame.
    :param row_index: row index of the instance to inspect.
    :param columns: either a list of columns or column indices to show. If specified,
                    parameter ``top_n_features`` will be ignored.
    :param top_n_features: a number of columns to pick using variable importance (where applicable).
    :param include_explanations: if specified, return only the specified model explanations
                                 (mutually exclusive with ``exclude_explanations``).
    :param exclude_explanations: exclude specified model explanations.
    :param plot_overrides: overrides for individual model explanations.
    :param qualitative_colormap: a colormap name.
    :param figsize: figure size; passed directly to matplotlib.
    :param render: if ``True``, render the model explanations; otherwise model explanations are just returned.

    :returns: H2OExplanation containing the model explanations including headers and descriptions.

    :examples:

    >>> import h2o
    >>> from h2o.automl import H2OAutoML
    >>>
    >>> h2o.init()
    >>>
    >>> # Import the wine dataset into H2O:
    >>> f = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
    >>> df = h2o.import_file(f)
    >>>
    >>> # Set the response
    >>> response = "quality"
    >>>
    >>> # Split the dataset into a train and test set:
    >>> train, test = df.split_frame([0.8])
    >>>
    >>> # Train an H2OAutoML
    >>> aml = H2OAutoML(max_models=10)
    >>> aml.train(y=response, training_frame=train)
    >>>
    >>> # Create the H2OAutoML explanation
    >>> aml.explain_row(test, row_index=0)
    >>>
    >>> # Create the leader model explanation
    >>> aml.leader.explain_row(test, row_index=0)
    """
    (is_aml, models_to_show, _, multinomial_classification, multiple_models,
     targets, tree_models_to_show, models_with_varimp) = _process_models_input(models, frame)

    if columns is not None and isinstance(columns, list):
        columns_of_interest = [frame.columns[col] if isinstance(col, int) else col for col in columns]
    else:
        if len(models_with_varimp) > 0:
            varimps = _consolidate_varimps(models_with_varimp[0])
            columns_of_interest = sorted(varimps.keys(), key=lambda k: -varimps[k])[
                                  :min(top_n_features, len(varimps))]
        else:
            import warnings
            warnings.warn("No model with variable importance. Selecting all features to explain.")
            columns_of_interest = _get_xy(models_to_show[0])[0]

    # Make sure that there are no string columns to explain as they are not supported by pdp
    # Usually those columns would not be used by algos so this just makes sure to exclude them
    # if user specifies top_n=float('inf') or columns_of_interest=x etc.
    dropped_string_columns = [col for col in columns_of_interest if frame.type(col) == "string"]
    if len(dropped_string_columns) > 0:
        warnings.warn("Dropping string columns as they are not supported: {}".format(dropped_string_columns))
        columns_of_interest = [col for col in columns_of_interest if frame.type(col) != "string"]

    possible_explanations = ["leaderboard", "shap_explain_row", "ice"]

    explanations = _process_explanation_lists(
        exclude_explanations=exclude_explanations,
        include_explanations=include_explanations,
        possible_explanations=possible_explanations
    )

    if render:
        display = _display
    else:
        display = _dont_display

    result = H2OExplanation()
    if multiple_models and "leaderboard" in explanations:
        result["leaderboard"] = H2OExplanation()
        result["leaderboard"]["header"] = display(Header("Leaderboard"))
        result["leaderboard"]["description"] = display(Description("leaderboard_row"))
        result["leaderboard"]["data"] = display(_get_leaderboard(models, row_index=row_index,
                                                                 **_custom_args(
                                                                     plot_overrides.get("leaderboard"),
                                                                     frame=frame)))

    if len(tree_models_to_show) > 0 and not multinomial_classification and \
            "shap_explain_row" in explanations:
        result["shap_explain_row"] = H2OExplanation()
        result["shap_explain_row"]["header"] = display(Header("SHAP Explanation"))
        result["shap_explain_row"]["description"] = display(Description("shap_explain_row"))
        for tree_model in tree_models_to_show:
            result["shap_explain_row"][tree_model.model_id] = display(shap_explain_row_plot(
                tree_model, row_index=row_index,
                **_custom_args(plot_overrides.get("shap_explain_row"),
                               frame=frame, figsize=figsize)))

    if "ice" in explanations and not multiple_models:
        result["ice"] = H2OExplanation()
        result["ice"]["header"] = display(Header("Individual Conditional Expectation"))
        result["ice"]["description"] = display(Description("ice_row"))
        result["ice"]["plots"] = H2OExplanation()
        for column in columns_of_interest:
            result["ice"]["plots"][column] = H2OExplanation()
            for target in targets:
                ice = display(pd_plot(
                    models_to_show[0], column=column,
                    row_index=row_index,
                    target=target,
                    **_custom_args(
                        plot_overrides.get("ice"),
                        frame=frame,
                        figsize=figsize,
                        colormap=qualitative_colormap
                    )))
                if target is None:
                    result["ice"]["plots"][column] = ice
                else:
                    result["ice"]["plots"][column][target[0]] = ice

    return result


# FAIRNESS
def _corrected_variance(accuracy, total):
    # From De-biasing bias measurement https://arxiv.org/pdf/2205.05770.pdf
    import numpy as np
    accuracy = np.array(accuracy)
    total = np.array(total)
    return max(0, np.var(accuracy - np.mean(accuracy * (1 - accuracy) / total)))


def disparate_analysis(models, frame, protected_columns, reference, favorable_class, air_metric="selectedRatio", alpha=0.05):
    """
     Create a frame containing aggregations of intersectional fairness across the models.

    :param models: List of H2O Models
    :param frame: H2OFrame
    :param protected_columns: List of categorical columns that contain sensitive information
                              such as race, gender, age etc.
    :param reference: List of values corresponding to a reference for each protected columns.
                      If set to ``None``, it will use the biggest group as the reference.
    :param favorable_class: Positive/favorable outcome class of the response.
    :param air_metric: Metric used for Adverse Impact Ratio calculation. Defaults to ``selectedRatio``.
    :param alpha: The alpha level is the probability of rejecting the null hypothesis that the protected group
                  and the reference came from the same population when the null hypothesis is true.

    :return: H2OFrame

    :examples:
    >>> from h2o.estimators import H2OGradientBoostingEstimator, H2OInfogram
    >>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/admissibleml_test/taiwan_credit_card_uci.csv")
    >>> x = ['LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2', 'PAY_3', 'PAY_4', 'PAY_5', 'PAY_6', 'BILL_AMT1', 'BILL_AMT2', 'BILL_AMT3',
    >>>      'BILL_AMT4', 'BILL_AMT5', 'BILL_AMT6', 'PAY_AMT1', 'PAY_AMT2', 'PAY_AMT3', 'PAY_AMT4', 'PAY_AMT5', 'PAY_AMT6']
    >>> y = "default payment next month"
    >>> protected_columns = ['SEX', 'EDUCATION']
    >>>
    >>> for c in [y] + protected_columns:
    >>>     data[c] = data[c].asfactor()
    >>>
    >>> train, test = data.split_frame([0.8])
    >>>
    >>> reference = ["1", "2"]  # university educated single man
    >>> favorable_class = "0"  # no default next month
    >>>
    >>> gbm1 = H2OGradientBoostingEstimator()
    >>> gbm1.train(x, y, train)
    >>>
    >>> gbm2 = H2OGradientBoostingEstimator(ntrees=5)
    >>> gbm2.train(x, y, train)
    >>>
    >>> h2o.explanation.disparate_analysis([gbm1, gbm2], test, protected_columns, reference, favorable_class)
    """
    import numpy as np
    from collections import defaultdict
    leaderboard = h2o.make_leaderboard(models, frame, extra_columns="ALL")
    additional_columns = defaultdict(list)
    models_dict = {m.model_id: m for m in models} if isinstance(models, list) else dict()
    for model_id in leaderboard[:, "model_id"].as_data_frame(False, False):
        model = models_dict.get(model_id[0], h2o.get_model(model_id[0]))
        additional_columns["num_of_features"].append(len(_get_xy(model)[0]))
        fm = model.fairness_metrics(frame=frame, protected_columns=protected_columns, reference=reference, favorable_class=favorable_class)
        overview = NumpyFrame(fm["overview"])
        additional_columns["var"].append(np.var(overview["accuracy"]))
        additional_columns["corrected_var"].append(_corrected_variance(overview["accuracy"], overview["total"]))

        selected_air_metric = "AIR_{}".format(air_metric)
        if selected_air_metric not in overview.columns:
            raise ValueError(
                "Metric {} is not present in the result of model.fairness_metrics. Please specify one of {}.".format(
                    air_metric,
                    ", ".join([m for m in overview.columns if m.startswith("AIR")])
                ))

        air = overview[selected_air_metric]
        additional_columns["air_min"].append(np.min(air))
        additional_columns["air_mean"].append(np.mean(air))
        additional_columns["air_median"].append(np.median(air))
        additional_columns["air_max"].append(np.max(air))
        additional_columns["cair"].append(np.sum([w * x for w, x in
                                                  zip(overview["relativeSize"], air)]))
        pvalue = overview["p.value"]

        def NaN_if_empty(agg, arr):
            if len(arr) == 0:
                return float("nan")
            return agg(arr)

        additional_columns["significant_air_min"].append(NaN_if_empty(np.min, air[pvalue < alpha]))
        additional_columns["significant_air_mean"].append(NaN_if_empty(np.mean, air[pvalue < alpha]))
        additional_columns["significant_air_median"].append(NaN_if_empty(np.median, air[pvalue < alpha]))
        additional_columns["significant_air_max"].append(NaN_if_empty(np.max, air[pvalue < alpha]))

        additional_columns["p.value_min"].append(np.min(pvalue))
        additional_columns["p.value_mean"].append(np.mean(pvalue))
        additional_columns["p.value_median"].append(np.median(pvalue))
        additional_columns["p.value_max"].append(np.max(pvalue))
    return leaderboard.cbind(h2o.H2OFrame(additional_columns))


