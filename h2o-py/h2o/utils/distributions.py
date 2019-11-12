# -*- encoding: utf-8 -*-
"""
Predefined distributions to use for custom distribution definition

:copyright: (c) 2019 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""


class CustomDistributionGeneric(object):
    """
        Predefined generic distribution class to use for customization distribution.

        The class has four methods:
            - link: link function transforms the probability of response variable to a continuous scale that is unbounded
            - init: computes numerator and denominator of the initial value.
            - gradient: computes (Negative half) Gradient of deviance function at predicted value for an actual response
            - gamma: computes numerator and denominator of terminal node estimate

        For customization a loss function, the gradient and gamma methods are important.

        To customize a special type of problem we recommend you to inherit from predefined classes:
            - CustomDistributionGaussian - for regression problems
            - CustomDistributionBernoulli - for 2-class classification problems
            - CustomDistributionMultinomial - for n-class classification problems
    """
    def link(self):
        """
        Type of Link Function.

        :return: name of link function. Possible functions: log, logit, identity, inverse, ologit, ologlog, oprobit
        """
        return "identity"

    def init(self, w, o, y):
        """
        Contribution for initial value computation (numerator and denominator).

        :param w: weight
        :param o: offset
        :param y: response
        :param l: class label (for multinomial classification only)
        :return: list [weighted contribution to init numerator,  weighted contribution to init denominator]
        """
        return [0, 0]

    def gradient(self, y, f, l=None):
        """
        (Negative half) Gradient of deviance function at predicted value f, for actual response y.
        Important fot customization of a loss function.

        :param y: actual response
        :param f: predicted response in link space including offset
        :return: gradient
        """
        return 0

    def gamma(self, w, y, z, f):
        """
        Contribution for GBM's leaf node prediction (numerator and denominator).
        Important for customization of a loss function.

        :param w: weight
        :param y: actual response
        :param z: residual
        :param f: predicted response including offset
        :return: list [weighted contribution to gamma numerator, weighted contribution to gamma denominator]
        """
        return 1


class CustomDistributionGaussian(CustomDistributionGeneric):
    """
        Predefined distribution class for regression problems.
    """

    def link(self):
        return "identity"

    def init(self, w, o, y):
        return [w * (y - o), w]

    def gradient(self, y, f):
        return y - f

    def gamma(self, w, y, z, f):
        return [w * z, w]


class CustomDistributionBernoulli(CustomDistributionGeneric):
    """
        Predefined distribution class for 2-class classification problems.
    """

    def exp(self, x):
        import java.lang.Math as Math
        max_exp = 1e19
        return Math.min(max_exp, Math.exp(x))

    def link(self):
        return "logit"

    def init(self, w, o, y):
        return [w * (y - o), w]

    def gradient(self, y, f):
        return y - (1 / (1 + self.exp(-f)))

    def gamma(self, w, y, z, f):
        ff = y - z
        return [w * z, w * ff * (1 - ff)]
    

class CustomDistributionMultinomial(CustomDistributionGeneric):
    """
        Predefined distribution class for n-class classification problems.
    """

    def link(self):
        return "log"

    def init(self, w, o, y):
        return [w * (y - o), w]

    def gradient(self, y, f, l):
        return 1 - f if y == l else 0 - f

    def gamma(self, w, y, z, f):
        import java.lang.Math as math
        absz = math.abs(z)
        return [w * z, w * (absz * (1 - absz))]
