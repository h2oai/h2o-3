# Import predefined Gaussian distribution
from h2o.utils.distributions import CustomDistributionGaussian

# Define asymmetric loss distribution from Gaussian distribution 
class AsymmetricLossDistribution(CustomDistributionGaussian):

    def gradient(self, y, f):
        error = y - f
        # more predicted items is better error than the fewer predicted items
        # if residual is positive there are not enough items in the store
        # if residual is negative or zero there are enough items in the store
        # the positive error should be set as bigger! 
        return error if error < 0 else 10 * error
