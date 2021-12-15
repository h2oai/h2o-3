import h2o
from .autoh2o import *


class Distribution:
    AUTO = 'AUTO'
    bernoulli = 'bernoulli'
    binomial = 'bernoulli'
    fractionalbinomial = 'fractionalbinomial'
    quasibinomial = 'quasibinomial'
    multinomial = 'multinomial'
    gaussian = 'gaussian'
    poisson = 'poisson'
    negativebinomial = 'negativebinomial'
    gamma = 'gamma'
    laplace = 'laplace'
    ordinal = 'ordinal'

    @staticmethod
    def tweedie(tweedie_power):
        assert 1 < tweedie_power < 2
        return dict(distribution='tweedie', tweedie_power=tweedie_power)

    @staticmethod
    def quantile(quantile_alpha):
        assert 0 <= quantile_alpha <= 1
        return dict(distribution='quantile', quantile_alpha=quantile_alpha)

    @staticmethod
    def huber(huber_alpha):
        assert 0 <= huber_alpha <= 1
        return dict(distribution='huber', huber_alpha=huber_alpha)

    @staticmethod
    def custom(custom_distribution_func):
        from h2o.utils.typechecks import is_type
        if not is_type(custom_distribution_func, str):
            custom_distribution_func = h2o.upload_custom_distribution(custom_distribution_func)
        return dict(distribution='custom', custom_distribution_func=custom_distribution_func)


__all__ = ['H2OAutoML', 'Distribution', 'get_automl', 'get_leaderboard']
>>>>>>> 901c6eb984 (Initial backend implementation)
