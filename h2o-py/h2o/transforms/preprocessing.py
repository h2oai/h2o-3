from .transform_base import H2OTransformer
import warnings


class H2OScaler(H2OTransformer):
    """
    Standardize an H2OFrame by demeaning and scaling each column.

    The default scaling will result in an H2OFrame with columns
    having zero mean and unit variance. Users may specify the
    centering and scaling values used in the standardization of
    the H2OFrame.
    """

    def __init__(self, center=True, scale=True):
        """
        :param center: A boolean or list of numbers. If True, then columns will be demeaned before scaling.
            If False, then columns will not be demeaned before scaling.
            If centers is an array of numbers, then len(centers) must match the number of
            columns in the dataset. Each value is removed from the respective column
            before scaling.
        :param scale: A boolean or list of numbers. If True, then columns will be scaled by the column's standard
            deviation. If False, then columns will not be scaled. If scales is an array, then len(scales) must match
            the number of columns in the dataset. Each column is scaled by the respective value in this array.
        :returns: An instance of H2OScaler.
        """
        self.parms = locals()
        self.parms = {k: v for k, v in self.parms.items() if k != "self"}
        if center is None or scale is None: raise ValueError("centers and scales must not be None.")
        self._means = None
        self._stds = None


    @property
    def means(self):
        return self._means


    @property
    def stds(self):
        return self._stds


    def fit(self, X, y=None, **params):
        """
        Fit this object by computing the means and standard deviations used by the transform method.

        :param X: An H2OFrame; may contain NAs and/or categoricals.
        :param y: None (Ignored)
        :param params: Ignored
        :returns: This H2OScaler instance
        """
        if isinstance(self.parms["center"], (tuple, list)): self._means = self.parms["center"]
        if isinstance(self.parms["scale"], (tuple, list)): self._stds = self.parms["scale"]
        if self.means is None and self.parms["center"]:
            self._means = X.mean(return_frame=True).getrow()
        else:
            self._means = False
        if self.stds is None and self.parms["scale"]:
            self._stds = X.sd()
        else:
            self._stds = False
        return self


    def transform(self, X, y=None, **params):
        """
        Scale an H2OFrame with the fitted means and standard deviations.

        :param X: An H2OFrame; may contain NAs and/or categoricals.
        :param y: None (Ignored)
        :param params: (Ignored)
        :returns: A scaled H2OFrame.
        """
        return X.scale(self.means, self.stds)


    def inverse_transform(self, X, y=None, **params):
        """
        Undo the scale transformation.

        :param X: An H2OFrame; may contain NAs and/or categoricals.
        :param y: None (Ignored)
        :param params: (Ignored)
        :returns: An H2OFrame
        """
        for i in range(X.ncol):
            X[i] = self.means[i] + self.stds[i] * X[i]
        return X




class H2OColSelect(H2OTransformer):

    def __init__(self, cols):
        self.cols = cols


    def fit(self, X, y=None, **params):
        return self


    def transform(self, X, y=None, **params):
        return X[self.cols]


    def to_rest(self, step_name):
        args = [step_name, "H2OColSelect", ("(cols_py dummy %r)" % self.cols), False, "|"]
        return super(H2OColSelect, self).to_rest(args)





class H2OColOp(H2OTransformer):
    """
    Perform a column operation. If inplace is True, then cbind the result onto original frame,
    otherwise, perform the operation in place.
    """

    def __init__(self, op, col=None, inplace=True, new_col_name=None, **params):
        self.fun = op
        self.col = col
        self.inplace = inplace
        self.params = params
        self.new_col_name = new_col_name
        if inplace and new_col_name is not None:
            warnings.warn("inplace was False, but new_col_name was not empty. Ignoring new_col_name.")
        if isinstance(col, (list, tuple)): raise ValueError("col must be None or a single column.")

    def fit(self, X, y=None, **params):
        return self

    def transform(self, X, y=None, **params):
        res = H2OColOp._transform_helper(X, params)
        if self.inplace:
            X[self.col] = res
        else:
            return X.cbind(res)
        return X

    def _transform_helper(self, X, **params):
        if not self.params:
            if self.col is not None:
                res = self.fun(X[self.col])
            else:
                res = self.fun(X)
        else:
            if self.col is not None:
                res = self.fun(X[self.col], **self.params)
            else:
                res = self.fun(X, **self.params)
        return res

    def to_rest(self, step_name):
        ast = self._transform_helper(self._dummy_frame())._ex._to_string()
        new_col_names = self.new_col_name
        if new_col_names is None:
            new_col_names = ["|"]
        elif not isinstance(new_col_names, (list, tuple)):
            new_col_names = [new_col_names]
        return super(H2OColOp, self).to_rest(
            [step_name, self.__class__.__name__, ast, self.inplace, "|".join(new_col_names)])




class H2OBinaryOp(H2OColOp):
    """Perform a binary operation on a column.

    If left is None, then the column will appear on the left in the operation; otherwise
    it will be appear on the right.

    A ValueError is raised if both left and right are None.
    """

    def __init__(self, op, col, inplace=True, new_col_name=None, left=None, right=None, **params):
        super(H2OBinaryOp, self).__init__(op, col, inplace, new_col_name, **params)
        self.left_is_col = isinstance(left, H2OCol)
        self.right_is_col = isinstance(right, H2OCol)
        self.left = left
        self.right = right
        if left is None and right is None:
            raise ValueError("left and right cannot both be None")

    def _transform_helper(self, X, **params):
        if self.left is None: return self.fun(X[self.col], X[self.right.col] if self.right_is_col else self.right)
        return self.fun(X[self.left.col] if self.left_is_col else self.left, X[self.col])



class H2OCol(object):
    """
    Wrapper class for H2OBinaryOp step's left/right args.

    Use if you want to signal that a column actually comes from the train to be fitted on.
    """

    def __init__(self, column):
        self.col = column

        # TODO: handle arbitrary (non H2OFrame) inputs -- sql, web, file, generated
