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
    
    
class CustomDistributionQuasibinomial(CustomDistributionGeneric):

    def exp(self, x):
        import java.lang.Math as Math
        max_exp = 1e19
        return Math.min(max_exp, Math.exp(x))
    
    def link(self):
        return "logit"

    def init(self, w, o, y):
        return [w * (y - o), w]

    def gradient(self, y, f):
        ff = (1 / (1 + self.exp(-f)))
        if ff == y:
            return 0
        elif ff > 1:
            return y / ff
        elif ff < 0:
            return (1 - y) / (ff - 1)
        else:
            return y - ff

    def gamma(self, w, y, z, f):
        ff = y - z
        return [w * z, w * ff * (1 - ff)]
    

class CustomDistributionModifiedHuber(CustomDistributionGeneric):
    
    def link(self):
        return "logit"

    def init(self, w, o, y):
        return [w if y==1 else 0, 0 if y==1 else w]

    def gradient(self, y, f):
        yf = (2 * y - 1) * f
        if yf < -1:
            return 2 * (2 * y - 1)
        elif yf > 1:
            return 0
        else:
            return -f * (2 * y - 1) * (2 * y - 1)

    def gamma(self, w, y, z, f):
        yf = (2 * y - 1) * f
        if yf < -1: 
            return [w * 4 * (2 * y - 1), -w * 4 * yf]
        elif yf > 1:
            return [0, 0]
        else: 
            return [w * 2 * (2 * y - 1) * (1 - yf), w * (1 - yf) * (1 - yf)]


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
    

class CustomDistributionPoisson(CustomDistributionGeneric):

    def exp(self, x):
        import java.lang.Math as Math
        max_exp = 1e19
        return Math.min(max_exp, Math.exp(x))
    
    def link(self):
        return "log"

    def init(self, w, o, y):
        return [w * y, w * self.exp(o)]
    
    def gradient(self, y, f):
        return y - self.exp(f)
    
    def gamma(self, w, y, z, f):
        return [w * y, w * (y - z)]


class CustomDistributionGamma(CustomDistributionGeneric):

    def exp(self, x):
        import java.lang.Math as Math
        max_exp = 1e19
        return Math.min(max_exp, Math.exp(x))

    def link(self):
        return "log"

    def init(self, w, o, y):
        return [w * y * self.exp(-o), w]
    
    def gradient(self, y, f):
        return y - self.exp(-f) - 1
    
    def gamma(self, w, y, z, f):
        return [w * (z + 1), w]
    
    
class CustomDistributionHuber(CustomDistributionGeneric):
    
    def delta(self):
        return 0.9

    def gradient(self, y, f):
        import java.lang.Math as Math
        if Math.abs(y - f) <= self.delta():
            return y - f
        else:
            return -self.delta() if f >= y else self.delta()
    
    
class CustomDistributionTweedie(CustomDistributionGeneric):

    def exp(self, x):
        import java.lang.Math as Math
        max_exp = 1e19
        return Math.min(max_exp, Math.exp(x))
    
    def power(self):
        return 1.5

    def init(self, w, o, y):
        return [w * y * self.exp(o * (1 - self.power())), w * self.exp(o * (2 - self.power()))]

    def gradient(self, y, f):
        return y * self.exp(f * (1 - self.power())) - self.exp(f * (2 - self.power()))
    
    def gamma(self, w, y, z, f):
        return [w * y * self.exp(f * (1 - self.power())), w * self.exp(f * (2 - self.power()))]
    

class CustomDistributionLaplace(CustomDistributionGeneric):

    def gradient(self, y, f):
        return -0.5 if f > y else 0.5
    

class CustomDistributionQuantile(CustomDistributionGeneric):
    
    def alpha(self):
        return 0.5

    def gradient(self, y, f):
        return 0.5 * self.alpha() if y > f else 0.5 * (self.alpha() - 1)
