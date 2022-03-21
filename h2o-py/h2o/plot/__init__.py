from ._matplotlib import get_matplotlib_pyplot
from ._polycollection import get_polycollection
from ._plot_result import decorate_plot_result, is_decorated_plot_result, RAISE_ON_FIGURE_ACCESS

__all__ = ["decorate_plot_result", "get_matplotlib_pyplot", "is_decorated_plot_result", "get_polycollection", "RAISE_ON_FIGURE_ACCESS"]
