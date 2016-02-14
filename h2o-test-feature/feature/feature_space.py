import itertools
from data_arg_space import *
from parameter_arg_space import *
from feature_arg_space_sample import FeatureArgSpaceSample

class FeatureArgSpace():
  def __init__(self, name=None, arg_subspaces=None):
    """
    A feature is simply a function that has been implemented in software. Like any other function, features have
    arguments, each with their own domain. Each argument domain is a dimension in the feature's argument space, which
    this class is used to represent.
    
    :param name: the name of the feature. (string)
    :param arg_subspaces: a list of argument domains, or subspaces, which compose the overall feature argument space.
                          see DataArgSpace and ParameterArgSpace for the specification of list elements. (tuple)
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
    Combine the samples take from argument subspaces. For example, given a DataArgSpace and FeatureArgSpace objects, 
    samplings of each subspace can be computed with DataArgSpace.sample() and FeatureArgSpace.sample(). The results may
    look something like
    
    [{"DataArgSpace.name":Dataset1}, {"DataArgSpace.name":Dataset2}, ...] and
    [{"ParameterArgSpace.name":Value1}, {"ParameterArgSpace.name":Value2}, ...], respectively. 
    
    This routine combines these samplings according to the `method` option. The result is a list of "points" in the 
    FeatureArgSpace, which may look something like
    
    [{"DataArgSpace.name":Dataset1, "ParameterArgSpace.name":Value1},
     {"DataArgSpace.name":Dataset2, "ParameterArgSpace.name":Value1},
     {"DataArgSpace.name":Dataset1, "ParameterArgSpace.name":Value2},
     {"DataArgSpace.name":Dataset2, "ParameterArgSpace.name":Value2}]

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
  def __init__(self): FeatureArgSpace.__init__(self, "cos", (RealDataArgSpace(),))

class ACosFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "acos", (MinusOneToOneDataArgSpace(),))

class CoshFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "cosh", (MinusTenToTenDataArgSpace(),))

class ACoshFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "acosh", (MinusOneToInfDataArgSpace(),))

class SinFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "sin", (RealDataArgSpace(),))

class ASinFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "asin", (MinusOneToOneDataArgSpace(),))

class SinhFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "sinh", (MinusTenToTenDataArgSpace(),))

class ASinhFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "asinh", (RealDataArgSpace(),))

class TanFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "tan", (RealDataArgSpace(),))

class ATanFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "atan", (RealDataArgSpace(),))

class TanhFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "tanh", (MinusTenToTenDataArgSpace(),))

class ATanhFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "atanh", (MinusOneToOneDataArgSpace(),))

class AbsFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "abs", (RealDataArgSpace(),))

class CeilingFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "ceiling", (RealDataArgSpace(),))

class DigammaFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "digamma", (ZeroToInfDataArgSpace(),))

class TrigammaFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "trigamma", (ZeroToInfDataArgSpace(),))

class ExpFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "exp", (MinusTenToTenDataArgSpace(),))

class Expm1FeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "expm1", (MinusTenToTenDataArgSpace(),))

class FloorFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "floor", (RealDataArgSpace(),))

class TruncFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "trunc", (RealDataArgSpace(),))

class GammaFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "gamma", (ZeroToTenDataArgSpace(),))

class IsCharFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "is.character", (IsCharDataArgSpace(),))

class IsNaFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "is.na", (IsNaDataArgSpace(),))

class IsNumericFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "is.numeric", (IsCharDataArgSpace(),))

class LGammaFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "lgamma", (ZeroToInfDataArgSpace(),))

class LevelsFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "h2o.levels", (LevelsDataArgSpace(),))

class LogFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "log", (ZeroToInfDataArgSpace(),))

class Log10FeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "log10", (ZeroToInfDataArgSpace(),))

class Log1pFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "log1p", (MinusOneToInfDataArgSpace(),))

class Log2FeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "log2", (ZeroToInfDataArgSpace(),))

class NLevelsFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "h2o.nlevels", (LevelsDataArgSpace(),))

class NcolFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "ncol", (NcolDataArgSpace(),))

class NrowFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "nrow", (NrowDataArgSpace(),))

class NotFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "!", (NotDataArgSpace(),))

class SignFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "sign", (RealDataArgSpace(),))

class SqrtFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "sqrt", (ZeroToInfDataArgSpace(),))

class RoundFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "round", (RealDataArgSpace(), DigitsParameterArgSpace()))

class SignifFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "signif", (RealDataArgSpace(), DigitsParameterArgSpace()))

class AndFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "&", (ZeroOneDataArgSpace(), ZeroOneDataArgSpace(name="y")))

class OrFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "|", (ZeroOneDataArgSpace(), ZeroOneDataArgSpace(name="y")))

class DivFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "/", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class ModFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "%%", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class IntDivFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "%/%", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class MultFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "*", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class PlusFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "+", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class PowFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "^", (MinusTenToTenDataArgSpace(),
                                                           MinusTenToTenDataArgSpace(name="y")))

class SubtFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "-", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class GEFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, ">=", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class GTFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, ">", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class LEFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "<=", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class LTFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "<", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class EQFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "==", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class NEFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "!=", (RealDataArgSpace(), RealDataArgSpace(name="y")))

class ScaleFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "scale", (RealDataArgSpace(),
                                                               CenterScaleParameterArgSpace(),
                                                               CenterScaleParameterArgSpace(name="scale")))

class AllFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "all", (AllDataArgSpace(),))

class CbindFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "h2o.cbind", (RealDataArgSpace(name="x"),
                                                                   RealDataArgSpace(name="y"),
                                                                   RealDataArgSpace(name="z")))

class ColnamesFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "colnames", (RealDataArgSpace(name="x", colnames=True),
                                                                  LogicalParameterArgSpace(name="do.NULL"),
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
                                        max_array_size=10,
                                        sort=True)]
    FeatureArgSpace.__init__(self, "[", (RealDataArgSpace(name="data"),
                                         ParameterArgSpace(name="row", value_spaces=i_j_value_spaces),
                                         ParameterArgSpace(name="col", value_spaces=i_j_value_spaces)))

class TableFeatureArgSpace(FeatureArgSpace):
  def __init__(self, two_col=False):
    if two_col: arg_subspaces = (TableDataArgSpace(two_col=two_col),)
    else: arg_subspaces = (TableDataArgSpace(), TableDataArgSpace(name="y"))
    FeatureArgSpace.__init__(self, "h2o.table", arg_subspaces=arg_subspaces)

class QuantileFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    data = RealDataArgSpace(na=False)
    data.cols_set = [1]
    data.rows_set = [100]
    FeatureArgSpace.__init__(self, "h2o.quantile", (data, ProbsParameterArgSpace()))

class CutFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    data = ZeroToOneDataArgSpace()
    data.cols_set = [1]
    data.rows_set = [100]
    FeatureArgSpace.__init__(self, "cut", (data,
                                           BreaksParameterArgSpace(),
                                           LogicalParameterArgSpace(name="include.lowest"),
                                           LogicalParameterArgSpace(name="right"),
                                           DigLabParameterArgSpace()))


class MatchFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "h2o.match", (MatchDataArgSpace(),
                                                                   MatchTableParameterArgSpace(),
                                                                   IntegerParameterArgSpace(name="nomatch"),
                                                                   MatchIncomparablesParameterArgSpace()))

class WhichFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    data = ZeroOneDataArgSpace()
    data.cols_set = [1]
    data.rows_set = [100]
    FeatureArgSpace.__init__(self, "h2o.which", (data,))

class RepLenFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    param = IntegerParameterArgSpace(name="length.out")
    param.value_spaces[0].lower = None
    param.value_spaces[0].upper = None
    param.value_spaces[0].set = [1, 10, 15, 42]
    FeatureArgSpace.__init__(self, "h2o.rep_len", (RealDataArgSpace(), param))

class StrSplitFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    param =  StringParameterArgSpace(name="split")
    param.value_spaces[0].upper = 1
    FeatureArgSpace.__init__(self, "h2o.strsplit", (StringDataArgSpace(), param))

class ToUpperFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "h2o.toupper", (StringDataArgSpace(),))

class TransposeFeatureArgSpace(FeatureArgSpace):
  def __init__(self): FeatureArgSpace.__init__(self, "t", (RealDataArgSpace(),))

class MMFeatureArgSpace(FeatureArgSpace):
  def __init__(self):
    data1 = RealDataArgSpace(name="x")
    data1.cols_set = [8]
    data1.rows_set = [10]
    data2 = RealDataArgSpace(name="y")
    data2.cols_set = [10]
    data2.rows_set = [8]
    FeatureArgSpace.__init__(self, "%*%", (data1, data2))

class VarFeatureArgSpace(FeatureArgSpace):
  def __init__(self, na=True):
    data2 = RealDataArgSpace(name="y", na=na)
    data2.null = True
    FeatureArgSpace.__init__(self, "h2o.var", (RealDataArgSpace(na=na),
                                               data2,
                                               LogicalParameterArgSpace(name="na.rm"),
                                               VarUseParameterArgSpace(na=na)))