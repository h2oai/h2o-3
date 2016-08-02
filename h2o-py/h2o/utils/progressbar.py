# -*- encoding: utf-8 -*-
"""
Text progress bar for long-running jobs.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import math
import os
import sys
import time
from types import FunctionType, GeneratorType, MethodType

import colorama
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type, numeric, is_str



class ProgressBar(object):
    """
    Component that controls execution of a remote job, and draws a progress bar as it goes along.

    Usage example::

        def progress_generator():
            for i in range(10):
                yield i + 1, 0.5

        from progressbar import ProgressBar, PBWBar, PBWPercentage
        pb = ProgressBar(maxval=10, widgets_tty=["Job progress:", PBWBar(), PBWPercentage()])
        pb.execute(progress_generator())

    One of the important features of this class is that it takes an active role in controlling the progress of the
    underlying process. In particular, the :meth:`execute` method takes a ``progress_fn`` parameter, which is a
    generator that the :meth:`execute` method will periodically call in order to find what the progress level is.
    The generator may return either a single value (the progress amount), or a tuple
    ``(progress_amount, poll_interval)``, where the second element specifies how long (in seconds) ProgressBar
    should wait before calling the generator again.

    Once the :meth:`execute` method is called, it will not return until the progress reaches 100%. However the progress
    function can raise ``StopIteration(message)`` in order to cancel the current progress (and if possible display the
    provided message, which should be short).

    Another feature of this class is that it models the progress as a smooth process, so that progress values reported
    by the executor are used merely as signals, and the class makes up its own mind regarding what the actual progress
    at each moment of time is. More specifically, this works as follows:

    Suppose x(t) is the progress level as a function of time t, and v(t) is its speed (i.e. v(t) = dx/dt). Let T be
    the expected time when progress reaches 100%. Suppose current progress level is x₀ and speed v₀ at time t₀. Ideally
    we'd want to move to x(T) = 1 at a constant speed, which would be (1-x₀)/(T-t₀). However unless current v₀ is
    exactly that, such strategy is not feasible (we do not allow sudden jumps in speed). Instead, we let the
    progress move with the speed v(t) = vₑ + (v₀ - vₑ)e⁻ᵝ⁽ᵗ⁻ᵗ⁰⁾, where vₑ is the target speed that has to be
    determined based on the requirement to reach 1 at time T, and β is the rate of convergence towards the target
    speed vₑ.

    After some simple calculus, we obtain
        x(t) = x₀ + vₑ·(t - t₀) + (v₀ - vₑ)/β·(1 - e⁻ᵝ⁽ᵗ⁻ᵗ⁰⁾),
        v(t) = vₑ + (v₀ - vₑ)e⁻ᵝ⁽ᵗ⁻ᵗ⁰⁾,  and
        vₑ = v₀ + β(1 - x₀ - v₀(T - t₀))/(β(T - t₀) - 1 + e⁻ᵝ⁽ᵀ⁻ᵗ⁰⁾)

    """

    # Minimum and maximum frequency for progress checks (i.e. do not query progress faster than every
    # MIN_PROGRESS_CHECK_INTERVAL seconds, or slower than every MAX_PROGRESS_CHECK_INTERVAL seconds).
    MIN_PROGRESS_CHECK_INTERVAL = 0.2
    MAX_PROGRESS_CHECK_INTERVAL = 5.0

    # Model parameter: rate of speed adjustment (in 1/s)
    BETA = 0.3

    # This parameter determines calculation of local speed progress.
    GAMMA = 0.6

    def __init__(self, title=None, widgets=None, maxval=1.0, file_mode=None):
        """
        Initialize the progress bar.

        :param title: name of the progress bar, to be used as the first default widget.
        :param maxval: when the progress reaches this value, the job is considered finished.
        :param widgets: list of widgets to render. Each widget is either a constant string, or an
            instance of a ProgressBarWidget subclass. Defaults are: [title+":", PBWBar(), PBWPercentage()].
        :param file_mode: if True, force file-mode output (i.e. progress is printed without ability to
            overwrite previous output).
        """
        assert_is_type(title, None, str)
        assert_is_type(maxval, numeric)
        assert_is_type(widgets, None, [str, ProgressBarWidget])
        assert_is_type(file_mode, None, bool)
        if title is None: title = "Progress"
        if file_mode is None: file_mode = not sys.stdout.isatty()

        self._maxval = maxval
        self._file_mode = file_mode
        self._widget = _ProgressBarCompoundWidget(widgets, title=title, file_mode=file_mode)

        # Variables needed for progress model (see docs).
        self._t0 = None  # Time when the model's parameters were computed
        self._x0 = None  # Progress level at t0
        self._v0 = None  # Progress speed at t0, in 1/s
        self._ve = None  # Target progress speed

        self._start_time = None
        self._progress_data = []  # list of tuples (time, progress_value) reported by the external source.


    def execute(self, progress_fn):
        """
        Start the progress bar, and return only when the progress reaches 100%.

        :param progress_fn: the executor function (or a generator). This function should take no arguments
            and return either a single number -- the current progress level, or a tuple (progress level, delay),
            where delay is the time interval for when the progress should be checked again. This function may at
            any point raise the ``StopIteration(message)`` exception, which will interrupt the progress bar,
            display the ``message`` in red font, and then re-raise the exception.
        :raises StopIteration: if the job is interrupted. The reason for interruption is provided in the exception's
            message. The message will say "cancelled" if the job was interrupted by the user by pressing Ctrl+C.
        """
        assert_is_type(progress_fn, FunctionType, GeneratorType, MethodType)
        if isinstance(progress_fn, GeneratorType):
            # Convert generator to a regular function
            progress_fn = (lambda g: lambda: next(g))(progress_fn)

        # Initialize the execution context
        colorama.init()
        self._start_time = time.time()
        next_progress_check = 0

        progress = 0
        status = None  # Status message in case the job gets interrupted.
        try:
            while True:
                # Query the progress level, but only if it's time already
                if next_progress_check <= time.time():
                    next_progress_check = self._query_progress(progress_fn)
                    self._recalculate_model_parameters()

                # Render the widget regardless of whether it's too early or not
                current_time = time.time()
                progress = self._compute_progress_at_time(current_time)[0]
                if progress >= 1: break
                result = self._widget.render(progress)
                assert_is_type(result, RenderResult)
                time0 = result.next_time
                time1 = self._get_time_at_progress(result.next_progress)
                next_render_time = min(time0, time1)
                self._draw(result.rendered)

                # Wait until the next rendering/querying cycle
                wait_time = min(next_render_time, next_progress_check) - current_time
                if wait_time > 0:
                    time.sleep(wait_time)
            progress = 1
        except KeyboardInterrupt:
            # If the user presses Ctrl+C, we interrupt the progress bar.
            status = "cancelled"
        except StopIteration as e:
            # If the generator raises StopIteration before reaching 100%, then the progress display will
            # reamin incomplete.
            status = str(e)

        # Do one final rendering before we exit
        result = self._widget.render(progress=progress, status=status)
        self._draw(result.rendered, final=True)

        if status == "cancelled":
            # Re-raise the exception, to inform the upstream caller that something unexpected happened.
            raise StopIteration(status)


    #-------------------------------------------------------------------------------------------------------------------
    #  Private
    #-------------------------------------------------------------------------------------------------------------------

    def _query_progress(self, progress_fn):
        """
        Check the current progress level by calling the provided executor function.

        :param progress_fn: same as ``progress_fn`` in :meth:`execute` (however it must be a regular function, not a
            generator).

        :returns: time moment when the next progress check should be made.
        :raises StopIteration: if the progress function stopped yielding results.
        """
        res = progress_fn()  # may raise StopIteration
        assert_is_type(res, (numeric, numeric), numeric)

        progress = res[0] if isinstance(res, tuple) else res
        now = time.time()
        self._progress_data.append((now, progress))
        delay = res[1] if isinstance(res, tuple) else self._guess_next_poll_interval()
        return now + max(min(delay, self.MAX_PROGRESS_CHECK_INTERVAL), self.MIN_PROGRESS_CHECK_INTERVAL)


    def _recalculate_model_parameters(self):
        """Compute t0, x0, v0, ve."""
        t_final = self._estimate_progress_completion_time()
        t0 = time.time()
        if t_final <= t0:
            ve = 0
            v0 = 0
            x0 = 1
        else:
            x0, v0 = self._compute_progress_at_time(t0)
            z = self.BETA * (t_final - t0)
            ve = v0 + (self.BETA * (1 - x0) - v0 * z) / (z - 1 + math.exp(-z))
        self._t0, self._x0, self._v0, self._ve = t0, x0, v0, ve


    def _estimate_progress_completion_time(self):
        """
        Estimate the moment when the underlying process is expected to reach completion.

        This function is invoked every time new physical data about the underlying process' status arrives.
        """
        if self._progress_data[-1][1] == self._maxval:
            return self._progress_data[-1][0]
        if self._progress_data[-1][1] == self._progress_data[0][1]:
            return self._progress_data[-1][0] + 90
        tlast, xlast = self._progress_data[-1]
        tacc, xacc = 0, 0
        factor = self.GAMMA
        for t, x in self._progress_data[-2::-1]:
            tacc += factor * (tlast - t)
            xacc += factor * (xlast - x)
            factor *= self.GAMMA
        if xacc == 0: return tlast + 300
        return tlast + tacc * (self._maxval - xlast) / xacc


    def _guess_next_poll_interval(self):
        """
        Determine when to query the progress status next.

        This function is called
        """
        if not self._progress_data: return 0
        time_elapsed = self._progress_data[-1][0] - self._start_time
        raw_progress = self._progress_data[-1][1]
        return min(0.2 * time_elapsed, 0.5 + (1 - raw_progress)**2)


    def _compute_progress_at_time(self, t):
        """
        Calculate the modelled progress state for the given time moment.

        :returns: tuple (x, v) of the progress level and progress speed.
        """
        if self._t0 is None: return (0, 0.01)
        t0, x0, v0, ve = self._t0, self._x0, self._v0, self._ve
        z = (v0 - ve) * math.exp(-self.BETA * (t - t0))
        vt = ve + z
        xt = x0 + ve * (t - t0) + (v0 - ve - z) / self.BETA
        return (min(xt, 1), vt)


    def _get_time_at_progress(self, x_target):
        """
        Return the projected time when progress level `x_target` will be reached.

        Since the underlying progress model is nonlinear, we need to do use Newton method to find a numerical solution
        to the equation x(t) = x_target.
        """
        t, x, v = self._t0, self._x0, self._v0
        # The convergence should be achieved in just few iterations, however in unlikely situation that it doesn't
        # we don't want to loop forever...
        for _ in range(20):
            if v == 0: return 1e20
            # make time prediction assuming the progress will continue at a linear speed ``v``
            t += (x_target - x) / v
            # calculate the actual progress at that time
            x, v = self._compute_progress_at_time(t)
            # iterate until convergence
            if abs(x - x_target) < 1e-3: return t
        return time.time() + 100



    def _draw(self, txt, final=False):
        """Print the rendered string to the stdout."""
        sys.stdout.write(txt)
        if final:
            sys.stdout.write("\n")
        else:
            if not self._file_mode:
                sys.stdout.write("\r")
            sys.stdout.flush()




#-----------------------------------------------------------------------------------------------------------------------
# Widgets (abstract)
#-----------------------------------------------------------------------------------------------------------------------

class RenderResult(object):
    """
    Helper class which serves as a return value from `ProgressBarWidget.render` method.

    It consists of the rendered string itself, and the schedule for when the widget has to be rendered again. The
    widget can request to be rerendered at a specific moment in time, or when the process reaches a certain progress
    level (or whichever comes sooner).
    """

    def __init__(self, rendered="", length=None, next_progress=1.0, next_time=1e20):
        """
        Initialize a new RenderResult instance.

        :param rendered: string representing the rendered widget.
        :param length: the character length of the rendered result (this could be different from ``len(rendered)`` if
            the result contains ANSI escape sequences).
        :param next_progress: request the widget to be rendered again when process' progress reaches this level.
        :param next_time: request the widget to be rendered again at this moment in time.
        """
        assert_is_type(rendered, str)
        assert_is_type(length, int, None)
        assert_is_type(next_progress, float)
        assert_is_type(next_time, int, float)
        self.rendered = rendered
        self.length = length if length is not None else len(rendered)
        self.next_progress = next_progress
        self.next_time = next_time



class ProgressBarWidget(object):
    """
    Base class for all progress bar widgets.

    The primary interface of this class is the ``render(progress)`` method, which requests the widget to render
    itself for the given progress level. The method returns a ``RenderResult`` object, consisting of: the rendered
    string itself, the next time moment when rendering should occur, or the next progress level at which the widget
    should be rerendered.
    """

    def __init__(self):
        """Initialize a progress bar widget."""
        self._file_mode = False

    def render(self, progress, width=None, status=None):
        """
        Render the widget.

        :param float progress: current job progress, a number between 0 and 1.
        :param int width: target character widths (for flexible widgets).
        :returns: a ``RenderResult`` object.
        """
        raise NotImplementedError()

    def set_encoding(self, encoding):
        """
        Inform the widget about the character encoding of the underlying output stream.

        Some widgets may render differently depending on whether the output is unicode-aware or not. This function
        will be called on the widget before its first render, to tell the widget what the encoding will be. It is up
        to the widget whether to do anything with this information or simply ignore it (default).

        :param encoding: the encoding string, eg "utf-8" or "cp437".
        """
        assert_is_type(encoding, str)

    def set_mode(self, mode):
        """
        Inform the widget that it will be rendered in either tty or file mode.

        This is only useful for widgets that support dual rendering mode.

        :param mode: either "tty" or "file".
        """
        assert_is_type(mode, "tty", "file")
        self._file_mode = mode == "file"



class ProgressBarFlexibleWidget(ProgressBarWidget):
    r"""
    Progress bar widgets deriving from this abstract class indicate that they can expand/shrink freely.

    Thus, this class behaves as LaTeX's \hfill, or CSS flex: "1 1 auto".
    """



class _ProgressBarCompoundWidget(ProgressBarWidget):
    """
    Container for other widgets.

    This widget is designed for internal use only! Its role is to lay out all contained widgets.
    """

    def __init__(self, widgets, title, file_mode):
        super(ProgressBarWidget, self).__init__()
        self._file_mode = file_mode
        self._width = min(self._get_terminal_size(), 100)
        self._encoding = (sys.stdout.encoding or "").lower()
        wlist = []
        for widget in (widgets or [title + ":", PBWBar(), PBWPercentage()]):
            if is_str(widget):
                widget = PBWString(widget)
            widget.set_mode("file" if file_mode else "tty")
            widget.set_encoding(self._encoding)
            wlist.append(widget)
        self._widgets = tuple(wlist)
        self._widget_lengths = self._compute_widget_sizes()
        self._rendered = ""


    def render(self, progress, width=None, status=None):
        """Render the widget."""
        results = [widget.render(progress, width=self._widget_lengths[i], status=status)
                   for i, widget in enumerate(self._widgets)]

        if self._file_mode:
            res = ""
            for i, result in enumerate(results):
                res += result.rendered
                if result.length < self._widget_lengths[i] and progress < 1: break
                res += " " if i < len(results) - 1 else ""
            rendered_str = res[len(self._rendered):]
            self._rendered = res
        else:
            rendered_str = " ".join(r.rendered for r in results)
        next_progress = min(r.next_progress for r in results)
        next_time = min(r.next_time for r in results)
        return RenderResult(rendered_str, next_progress=next_progress, next_time=next_time)


    def _compute_widget_sizes(self):
        """Initial rendering stage, done in order to compute widths of all widgets."""
        wl = [0] * len(self._widgets)
        flex_count = 0

        # First render all non-flexible widgets
        for i, widget in enumerate(self._widgets):
            if isinstance(widget, ProgressBarFlexibleWidget):
                flex_count += 1
            else:
                wl[i] = widget.render(1).length

        remaining_width = self._width - sum(wl)
        remaining_width -= len(self._widgets) - 1  # account for 1-space interval between widgets
        remaining_width = max(remaining_width, 10 * flex_count)  # Ensure at least 10 chars per flexible widget

        for i, widget in enumerate(self._widgets):
            if isinstance(widget, ProgressBarFlexibleWidget):
                target_length = int(remaining_width / flex_count)
                result = widget.render(1, target_length)
                wl[i] = result.length
                remaining_width -= result.length
                flex_count -= 1

        return wl


    @staticmethod
    def _get_terminal_size():
        """Find current STDOUT's width, in characters."""
        # If output is not terminal but a regular file, assume 100 chars width
        if not sys.stdout.isatty():
            return 100

        # Otherwise, first try getting the dimensions from shell command `stty`:
        try:
            import subprocess
            ret = subprocess.check_output(["stty", "size"]).strip().split(" ")
            if len(ret) == 2:
                return int(ret[1])
        except:
            pass

        # Otherwise try using ioctl
        try:
            from termios import TIOCGWINSZ
            from fcntl import ioctl
            from struct import unpack
            res = unpack("hh", ioctl(sys.stdout, TIOCGWINSZ, b"1234"))
            return int(res[1])
        except:
            pass

        # Finally check the COLUMNS environment variable
        return int(os.environ.get("COLUMNS", 100))



#-----------------------------------------------------------------------------------------------------------------------
# Widgets (implementations)
#-----------------------------------------------------------------------------------------------------------------------

class PBWString(ProgressBarWidget):
    """Widget that represents a fixed string."""

    def __init__(self, text):
        """Initialize the widget."""
        super(ProgressBarWidget, self).__init__()
        self._str = str(text)

    def render(self, progress, width=None, status=None):
        """Render the widget."""
        return RenderResult(self._str)



class PBWBar(ProgressBarFlexibleWidget):
    """
    The widget that looks like an actual progress bar.

    It renders differently depending on whether the output supports Unicode or not:
        |███████   |  or
        [#######   ]
    """

    def __init__(self):
        """Initialize the widget."""
        super(ProgressBarWidget, self).__init__()
        self._bar_ends = None
        self._bar_symbols = None
        self._rendered = ""
        self.set_encoding(None)

    def render(self, progress, width, status=None):
        """Render the widget."""
        if width <= 3: return RenderResult()
        bar_width = width - 2  # Total width minus the bar ends

        n_chars = int(progress * bar_width + 0.001)
        endf, endl = self._bar_ends
        if self._file_mode:
            out = endf
            out += self._bar_symbols[-1] * n_chars
            out += endl if progress == 1 else ""
            if status:
                out += " (%s)" % status
            next_progress = (n_chars + 1) / bar_width
            rendered_len = len(out)
        else:
            frac_chars = int((progress * bar_width - n_chars) * len(self._bar_symbols))
            out = endf
            out += self._bar_symbols[-1] * n_chars
            out += self._bar_symbols[frac_chars - 1] if frac_chars > 0 else ""
            rendered_len = len(out)
            if status:
                out += colorama.Fore.LIGHTRED_EX + " (" + status + ")" + colorama.Style.RESET_ALL
                rendered_len += 3 + len(status)
            out += " " * (width - 1 - rendered_len)
            out += endl
            next_progress = (n_chars + (frac_chars + 1) / len(self._bar_symbols)) / bar_width
            rendered_len += max(0, width - 1 - rendered_len) + 1
        return RenderResult(rendered=out, length=rendered_len, next_progress=next_progress)

    def set_encoding(self, encoding):
        """Inform the widget about the encoding of the underlying character stream."""
        self._bar_ends = "[]"
        self._bar_symbols = "#"
        if not encoding: return
        s1 = "\u258F\u258E\u258D\u258C\u258B\u258A\u2589\u2588"
        s2 = "\u258C\u2588"
        s3 = "\u2588"
        if self._file_mode:
            s1 = s2 = None
        assert len(s3) == 1
        for s in (s1, s2, s3):
            if s is None: continue
            try:
                s.encode(encoding)
                self._bar_ends = "||"
                self._bar_symbols = s
                return
            except UnicodeDecodeError:
                pass
            except LookupError:
                print("Warning: unknown encoding %s" % encoding)



class PBWPercentage(ProgressBarWidget):
    """
    Simple percentage indicator.

    Renders like this: " 87%" (has constant width of 4 chars). Tries to re-render at every percentage point
    (so that you will see " 88%", " 89%", " 90%", etc).
    """

    def render(self, progress, width=None, status=None):
        """Render the widget."""
        current_pct = int(progress * 100 + 0.1)
        return RenderResult(rendered="%3d%%" % current_pct, next_progress=(current_pct + 1) / 100)
