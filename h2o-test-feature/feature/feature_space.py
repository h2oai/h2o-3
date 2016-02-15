import itertools
from arg_space import *
from feature_space_sample import FeatureArgSpaceSample

class FeatureArgSpace():
  def __init__(self, name=None, arg_subspaces=None):
    """
    A feature is simply a function that has been implemented in software. Like any other function, features have
    arguments, each with their own domain. Each argument domain is a dimension in the feature's argument space, which
    this class is used to represent.
    
    :param name: the name of the feature. (string)
    :param arg_subspaces: a list of argument domains, or subspaces, which compose the overall feature argument space.
                          see ArgSpace and ArgSpace for the specification of list elements. (tuple)
    """

    self.name = name
    self.arg_subspaces = arg_subspaces

  def sample(self):
    """
    Take a random sample of points in each argument's sub-space. Combine the individual argument points to form
    a list of feature points.
    
    :return: list of randomly sampled points in the FeatureArgSpace. (list)
    """

    points = self.combine_arg_samples([arg.sample() for arg in self.arg_subspaces])
    return FeatureArgSpaceSample(name=self.name, points=points)

  def combine_arg_samples(self, arg_samples, method="cartesian"):
    """
    Combine the samples take from argument subspaces. For example, given a ArgSpace and FeatureArgSpace objects, 
    samplings of each subspace can be computed with ArgSpace.sample() and FeatureArgSpace.sample(). The results may
    look something like
    
    [{"ArgSpace.name":Dataset1}, {"ArgSpace.name":Dataset2}, ...] and
    [{"ArgSpace.name":Value1}, {"ArgSpace.name":Value2}, ...], respectively. 
    
    This routine combines these samplings according to the `method` option. The result is a list of "points" in the 
    FeatureArgSpace, which may look something like
    
    [{"ArgSpace.name":Dataset1, "ArgSpace.name":Value1},
     {"ArgSpace.name":Dataset2, "ArgSpace.name":Value1},
     {"ArgSpace.name":Dataset1, "ArgSpace.name":Value2},
     {"ArgSpace.name":Dataset2, "ArgSpace.name":Value2}]

    :param arg_samples: a list of samplings from various argument subspaces. (list)
    :param method: the method of combination ("cartesian" is the default). (string)
    :return: a list of "feature points" in the FeatureArgSpace, where a "feature point" is a dictionary, where the keys
             are the names of the feature's arguments, and the values are "argument points" in the respective argument
             sub-spaces.
    """

    points = []
    if method == "cartesian":
      for p in itertools.product(*arg_samples):
        point = {}
        for kv in p: point.update(kv)
        points.append(point)
    else:
      raise(ValueError, "Only cartesian method is implemented!")

    return points

"""
########################################################################################################################
                                          Some specific FeatureArgSpaces
########################################################################################################################
"""

class CosFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "cos", (RealArgSpace(),))

class ACosFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "acos", (MinusOneToOneArgSpace(),))

class CoshFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "cosh", (MinusTenToTenArgSpace(),))

class ACoshFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "acosh", (MinusOneToInfArgSpace(),))

class SinFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "sin", (RealArgSpace(),))

class ASinFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "asin", (MinusOneToOneArgSpace(),))

class SinhFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "sinh", (MinusTenToTenArgSpace(),))

class ASinhFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "asinh", (RealArgSpace(),))

class TanFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "tan", (RealArgSpace(),))

class ATanFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "atan", (RealArgSpace(),))

class TanhFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "tanh", (MinusTenToTenArgSpace(),))

class ATanhFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "atanh", (MinusOneToOneArgSpace(),))

class AbsFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "abs", (RealArgSpace(),))

class CeilingFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "ceiling", (RealArgSpace(),))

class DigammaFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "digamma", (ZeroToInfArgSpace(),))

class TrigammaFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "trigamma", (ZeroToInfArgSpace(),))

class ExpFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "exp", (MinusTenToTenArgSpace(),))

class Expm1FeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "expm1", (MinusTenToTenArgSpace(),))

class FloorFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "floor", (RealArgSpace(),))

class TruncFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "trunc", (RealArgSpace(),))

class GammaFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "gamma", (ZeroToTenArgSpace(),))

class IsCharFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "is.character", (IsCharArgSpace(),))

class IsNaFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "is.na", (IsNaArgSpace(),))

class IsNumericFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "is.numeric", (IsCharArgSpace(),))

class LGammaFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "lgamma", (ZeroToInfArgSpace(),))

class LevelsFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "h2o.levels", (LevelsArgSpace(),))

class LogFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "log", (ZeroToInfArgSpace(),))

class Log10FeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "log10", (ZeroToInfArgSpace(),))

class Log1pFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "log1p", (MinusOneToInfArgSpace(),))

class Log2FeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "log2", (ZeroToInfArgSpace(),))

class NLevelsFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "h2o.nlevels", (LevelsArgSpace(),))

class NcolFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "ncol", (NcolArgSpace(),))

class NrowFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "nrow", (NrowArgSpace(),))

class NotFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "!", (NotArgSpace(),))

class SignFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "sign", (RealArgSpace(),))

class SqrtFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "sqrt", (ZeroToInfArgSpace(),))

class RoundFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "round", (RealArgSpace(), DigitsArgSpace()))

class SignifFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "signif", (RealArgSpace(), DigitsArgSpace()))

class AndFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "&", (ZeroOneArgSpace(), ZeroOneArgSpace(name="y")))

class OrFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "|", (ZeroOneArgSpace(), ZeroOneArgSpace(name="y")))

class DivFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "/", (RealArgSpace(), RealArgSpace(name="y")))

class ModFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "%%", (RealArgSpace(), RealArgSpace(name="y")))

class IntDivFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "%/%", (RealArgSpace(), RealArgSpace(name="y")))

class MultFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "*", (RealArgSpace(), RealArgSpace(name="y")))

class PlusFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "+", (RealArgSpace(), RealArgSpace(name="y")))

class PowFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "^", (MinusTenToTenArgSpace(),
                                                           MinusTenToTenArgSpace(name="y")))

class SubtFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "-", (RealArgSpace(), RealArgSpace(name="y")))

class GEFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, ">=", (RealArgSpace(), RealArgSpace(name="y")))

class GTFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, ">", (RealArgSpace(), RealArgSpace(name="y")))

class LEFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "<=", (RealArgSpace(), RealArgSpace(name="y")))

class LTFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "<", (RealArgSpace(), RealArgSpace(name="y")))

class EQFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "==", (RealArgSpace(), RealArgSpace(name="y")))

class NEFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "!=", (RealArgSpace(), RealArgSpace(name="y")))

class ScaleFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "scale", (RealArgSpace(),
                                                               CenterScaleArgSpace(),
                                                               CenterScaleArgSpace(name="scale")))

class AllFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "all", (AllArgSpace(),))

class CbindFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "h2o.cbind", (RealArgSpace(name="x"),
                                                                   RealArgSpace(name="y"),
                                                                   RealArgSpace(name="z")))

class ColnamesFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "colnames", (RealArgSpace(name="x", colnames=True),
                                                                  LogicalArgSpace(name="do.NULL"),
                                                                  StringParameterArgSpace(name="prefix")))

class SliceFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    i_j_value_spaces = [ScalerValueSpace(space_type="integer",
                                         lower=1,
                                         upper=10),
                        ArrayValueSpace(space_type="integer[]",
                                        element_value_space=ScalerValueSpace(space_type="integer",
                                                                             lower=1,
                                                                             upper=10),
                                        lower_array_size=1,
                                        upper_array_size=10,
                                        sort=True)]
    FeatureArgSpace.__init__(self, "[", (RealArgSpace(name="data"),
                                         ArgSpace(name="row", value_spaces=i_j_value_spaces),
                                         ArgSpace(name="col", value_spaces=i_j_value_spaces)))

class TableFeatureArgSpace(FeatureArgSpace):
  def __init__(self, two_col=False):
    if two_col: arg_subspaces = (TableArgSpace(two_col=two_col),)
    else: arg_subspaces = (TableArgSpace(), TableArgSpace(name="y"))
    FeatureArgSpace.__init__(self, "h2o.table", arg_subspaces=arg_subspaces)

class QuantileFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    data = RealArgSpace(na=False)
    data.cols_set = [1]
    data.rows_set = [100]
    FeatureArgSpace.__init__(self, "h2o.quantile", (data, ProbsArgSpace()))

class CutFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    data = ZeroToOneArgSpace()
    data.cols_set = [1]
    data.rows_set = [100]
    FeatureArgSpace.__init__(self, "cut", (data,
                                           BreaksArgSpace(),
                                           LogicalArgSpace(name="include.lowest"),
                                           LogicalArgSpace(name="right"),
                                           DigLabArgSpace()))


class MatchFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "h2o.match", (MatchArgSpace(),
                                                                   MatchTableArgSpace(),
                                                                   IntegerArgSpace(name="nomatch"),
                                                                   MatchIncomparablesArgSpace()))

class WhichFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    data = ZeroOneArgSpace()
    data.cols_set = [1]
    data.rows_set = [100]
    FeatureArgSpace.__init__(self, "h2o.which", (data,))

class RepLenFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    param = IntegerArgSpace(name="length.out")
    param.value_spaces[0].lower = None
    param.value_spaces[0].upper = None
    param.value_spaces[0].set = [Value(value_type="integer", value=1),
                                 Value(value_type="integer", value=10),
                                 Value(value_type="integer", value=15),
                                 Value(value_type="integer", value=42)]
    FeatureArgSpace.__init__(self, "h2o.rep_len", (RealArgSpace(), param))

class StrSplitFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    param =  StringParameterArgSpace(name="split")
    param.value_spaces[0].upper = 1
    FeatureArgSpace.__init__(self, "h2o.strsplit", (StringDataArgSpace(), param))

class ToUpperFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "h2o.toupper", (StringDataArgSpace(),))

class TransposeFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "t", (RealArgSpace(),))

class MMFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    data1 = RealArgSpace(name="x")
    data1.cols_set = [8]
    data1.rows_set = [10]
    data2 = RealArgSpace(name="y")
    data2.cols_set = [10]
    data2.rows_set = [8]
    FeatureArgSpace.__init__(self, "%*%", (data1, data2))

class VarFeatureArgSpace(FeatureArgSpace):
  def __init__(self, na=True):
    FeatureArgSpace.__init__(self, "h2o.var", (RealArgSpace(na=na),
                                               RealArgSpace(name="y", na=na, null=True),
                                               LogicalArgSpace(name="na.rm"),
                                               VarUseArgSpace(na=na)))