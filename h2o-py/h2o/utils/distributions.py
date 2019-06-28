# -*- encoding: utf-8 -*-
"""
Predefined distributions to use for custom distribution definition

:copyright: (c) 2019 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""


class CustomDistributionGeneric:
    def link(self):
        return "identity"

    def init(self, w, o, y):
        return [0, 0]

    def gradient(self, y, f):
        return 0

    def gamma(self, w, y, z, f):
        return 0


class CustomDistributionGaussian(CustomDistributionGeneric):

    def link(self):
        return "identity"

    def init(self, w, o, y):
        return [w * (y - o), w]

    def gradient(self, y, f):
        return y - f

    def gamma(self, w, y, z, f):
        return [w * z, w]


class CustomDistributionBernoulli(CustomDistributionGeneric):

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

    def link(self):
        return "log"

    def init(self, w, o, y):
        return [w * (y - o), w]

    def gradient(self, y, f):
        return y - f

    def gamma(self, w, y, z, f):
        import java.lang.Math as math
        absz = math.abs(z)
        return [w * z, w * (absz * (1 - absz))]
