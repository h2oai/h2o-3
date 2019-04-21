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
from h2o.utils.shared_utils import clamp
from h2o.utils.typechecks import assert_is_type, is_type, numeric


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

    In order to ensure smoothness of the model, we need to be careful as the progress approaches 100% and t
    approaches T. First, under no circumstance we are allowed to finish the progress if the underlying process did
    not report 100% status. At the same time we don't want to jump suddenly from, say, 90% to 100% at the final step.
    Thus, we allow the model to progress for a very short amount of time after the process has completed.
    """

    # Minimum and maximum frequency for progress checks (i.e. do not query progress faster than every
    # MIN_PROGRESS_CHECK_INTERVAL seconds, or slower than every MAX_PROGRESS_CHECK_INTERVAL seconds).
    MIN_PROGRESS_CHECK_INTERVAL = 0.2
    MAX_PROGRESS_CHECK_INTERVAL = 5.0

    # Model parameter: rate of speed adjustment (in 1/s)
    BETA = 0.3

    # This parameter determines calculation of local speed progress.
    GAMMA = 0.6

    # How long are we allowed to wait after the progress is finished before returning.
    FINISH_DELAY = 0.3

    def __init__(self, title=None, widgets=None, maxval=1.0, file_mode=None, hidden=False):
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


        # Fix for PUBDEV-5048. H2O depends on isatty attribute, but some Python Notebooks override stdout and doesn't
        # specify it. The same holds for the encoding attribute bellow
        if not hasattr(sys.stdout, "isatty"):
            sys.stdout.isatty = lambda: False

        if not hasattr(sys.stdout, "encoding"):
            sys.stdout.encoding = sys.getdefaultencoding()

        if title is None: title = "Progress"
        if file_mode is None: file_mode = not sys.stdout.isatty()

        self._maxval = maxval
        self._file_mode = file_mode
        if hidden:
            self._widget = _HiddenWidget()
            self._file_mode = True
        else:
            self._widget = _ProgressBarCompoundWidget(widgets, title=title, file_mode=file_mode)

        # Variables needed for progress model (see docs).
        self._t0 = None  # Time when the model's parameters were computed
        self._x0 = None  # Progress level at t0, a real number from 0 to 1
        self._v0 = None  # Progress speed at t0, in 1/s
        self._ve = None  # Target progress speed

        # List of tuples (timestamp, progress_value) reported by the external source. The timestamps are taken from
        # the moment the data was received, not when it was requested. The progress values are raw (i.e. they range
        # from 0 to self._maxval).
        self._progress_data = []
        # Timestamp when should the progress be queried next.
        self._next_poll_time = None


    def execute(self, progress_fn, print_verbose_info=None):
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
        self._next_poll_time = 0
        self._t0 = time.time()
        self._x0 = 0
        self._v0 = 0.01  # corresponds to 100s completion time
        self._ve = 0.01

        progress = 0
        status = None  # Status message in case the job gets interrupted.
        try:
            while True:
                # We attempt to synchronize all helper functions, ensuring that each of them has the same idea
                # for what the current time moment is. Otherwise we could have some corner cases when one method
                # says that something must happen right now, while the other already sees that moment in the past.
                now = time.time()

                # Query the progress level, but only if it's time already
                if self._next_poll_time <= now:
                    res = progress_fn()  # may raise StopIteration
                    assert_is_type(res, (numeric, numeric), numeric)
                    if not isinstance(res, tuple):
                        res = (res, -1)
                    # Progress querying could have taken some time, so update the current time moment
                    now = time.time()
                    self._store_model_progress(res, now)
                    self._recalculate_model_parameters(now)

                # Render the widget regardless of whether it's too early or not
                progress = min(self._compute_progress_at_time(now)[0], 1)
                if progress == 1 and self._get_real_progress() >= 1:
                    # Do not exit until both the model and the actual progress reach 100% mark.
                    break
                result = self._widget.render(progress)
                assert_is_type(result, RenderResult)
                time0 = result.next_time
                time1 = self._get_time_at_progress(result.next_progress)
                next_render_time = min(time0, time1)
                self._draw(result.rendered)

                # Wait until the next rendering/querying cycle
                wait_time = min(next_render_time, self._next_poll_time) - now
                if wait_time > 0:
                    time.sleep(wait_time)
                    if print_verbose_info is not None:
                        print_verbose_info(progress)
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

    def _get_real_progress(self):
        return self._progress_data[-1][1] / self._maxval

    def _store_model_progress(self, res, now):
        """
        Save the current model progress into ``self._progress_data``, and update ``self._next_poll_time``.

        :param res: tuple (progress level, poll delay).
        :param now: current timestamp.
        """
        raw_progress, delay = res
        raw_progress = clamp(raw_progress, 0, self._maxval)
        self._progress_data.append((now, raw_progress))

        if delay < 0:
            # calculation of ``_guess_next_poll_interval()`` should be done only *after* we pushed the fresh data to
            # ``self._progress_data``.
            delay = self._guess_next_poll_interval()
        self._next_poll_time = now + clamp(delay, self.MIN_PROGRESS_CHECK_INTERVAL, self.MAX_PROGRESS_CHECK_INTERVAL)


    def _recalculate_model_parameters(self, now):
        """Compute t0, x0, v0, ve."""
        time_until_end = self._estimate_progress_completion_time(now) - now
        assert time_until_end >= 0, "Estimated progress completion cannot be in the past."
        x_real = self._get_real_progress()
        if x_real == 1:
            t0, x0, v0, ve = now, 1, 0, 0
        else:
            x0, v0 = self._compute_progress_at_time(now)
            t0 = now
            if x0 >= 1:
                # On rare occasion, the model's progress may have reached 100% by ``now``. This can happen if
                # (1) the progress is close to 100% initially and has high speed, (2) on the previous call we
                # estimated that the process completion time will be right after the next poll time, and (3)
                # the polling itself took so much time that the process effectively "overshoot".
                # If this happens, then we adjust x0, v0 to the previous valid data checkpoint.
                t0, x0, v0 = self._t0, self._x0, self._v0
                time_until_end += now - t0
            z = self.BETA * time_until_end
            max_speed = (1 - x_real**2) / self.FINISH_DELAY
            ve = v0 + (self.BETA * (1 - x0) - v0 * z) / (z - 1 + math.exp(-z))
            if ve < 0:
                # Current speed is too high -- reduce v0 (violate non-smoothness of speed)
                v0 = self.BETA * (1 - x0) / (1 - math.exp(-z))
                ve = 0
            if ve > max_speed:
                # Current speed is too low: finish later, but do not allow ``ve`` to be higher than ``max_speed``
                ve = max_speed
        self._t0, self._x0, self._v0, self._ve = t0, x0, v0, ve


    def _estimate_progress_completion_time(self, now):
        """
        Estimate the moment when the underlying process is expected to reach completion.

        This function should only return future times. Also this function is not allowed to return time moments less
        than self._next_poll_time if the actual progress is below 100% (this is because we won't know that the
        process have finished until we poll the external progress function).
        """
        assert self._next_poll_time >= now
        tlast, wlast = self._progress_data[-1]
        # If reached 100%, make sure that we finish as soon as possible, but maybe not immediately
        if wlast == self._maxval:
            current_completion_time = (1 - self._x0) / self._v0 + self._t0
            return clamp(current_completion_time, now, now + self.FINISH_DELAY)

        # Calculate the approximate speed of the raw progress based on recent data
        tacc, wacc = 0, 0
        factor = self.GAMMA
        for t, x in self._progress_data[-2::-1]:
            tacc += factor * (tlast - t)
            wacc += factor * (wlast - x)
            factor *= self.GAMMA
            if factor < 1e-2: break

        # If there was no progress at all, then just assume it's 5 minutes from now
        if wacc == 0: return now + 300

        # Estimate the completion time assuming linear progress
        t_estimate = tlast + tacc * (self._maxval - wlast) / wacc

        # Adjust the estimate if it looks like it may happen too soon
        if t_estimate <= self._next_poll_time:
            t_estimate = self._next_poll_time + self.FINISH_DELAY

        return t_estimate


    def _guess_next_poll_interval(self):
        """
        Determine when to query the progress status next.

        This function is used if the external progress function did not return time interval for when it should be
        queried next.
        """
        time_elapsed = self._progress_data[-1][0] - self._progress_data[0][0]
        real_progress = self._get_real_progress()
        return min(0.2 * time_elapsed, 0.5 + (1 - real_progress)**0.5)


    def _compute_progress_at_time(self, t):
        """
        Calculate the modelled progress state for the given time moment.

        :returns: tuple (x, v) of the progress level and progress speed.
        """
        t0, x0, v0, ve = self._t0, self._x0, self._v0, self._ve
        z = (v0 - ve) * math.exp(-self.BETA * (t - t0))
        vt = ve + z
        xt = clamp(x0 + ve * (t - t0) + (v0 - ve - z) / self.BETA, 0, 1)
        return xt, vt


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
        if not self._file_mode:
            # If the user presses Ctrl+C this ensures we still start writing from the beginning of the line
            sys.stdout.write("\r")
        sys.stdout.write(txt)
        if final and not isinstance(self._widget, _HiddenWidget):
            sys.stdout.write("\n")
        else:
            if not self._file_mode:
                sys.stdout.write("\r")
            sys.stdout.flush()


    def __repr__(self):
        """Progressbar internal state (for debug purposes)."""
        t0 = self._progress_data[0][0]
        data = ",".join("(%.1f,%.3f)" % (t - t0, w / self._maxval) for t, w in self._progress_data)
        return "<Progressbar x0=%.3f, v0=%.3f, xraw=%.3f; data:[%s]>" % \
            (self._x0, self._v0, self._get_real_progress(), data)




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
        :param str status: if the job has finished unexpectedly, this will be a message explaining the reason.
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

    def render(self, progress, width=None, status=None):
        """Render the widget."""
        raise NotImplementedError()


class _HiddenWidget(ProgressBarWidget):
    """Widget that doesn't render anything (for hidden progress bars)."""
    def render(self, progress, width=None, status=None):
        """Return empty render result."""
        return RenderResult()


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
            if is_type(widget, str):
                widget = PBWString(widget)
            widget.set_mode("file" if file_mode else "tty")
            widget.set_encoding(self._encoding)
            wlist.append(widget)
        self._to_render = None  # Render this string on the next rendering cycle. Rarely used.
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
            if self._to_render:
                rendered_str = self._to_render + rendered_str
                self._to_render = None
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
        if remaining_width < 10 * flex_count:
            if self._file_mode:
                remaining_width = 10 * flex_count
            else:
                # The window is too small to accomodate the widget: try to split it into several lines, otherwise
                # switch to "file mode". If we don't do this, then rendering the widget will cause it to wrap, and
                # then when we use \r to go to the beginning of the line, only part of the widget will be overwritten,
                # which means we'll have many (possibly hundreds) of progress bar lines in the end.
                widget0 = self._widgets[0]
                if isinstance(widget0, PBWString) and remaining_width + widget0.render(0).length >= 10 * flex_count:
                    remaining_width += widget0.render(0).length + 1
                    self._to_render = widget0.render(0).rendered + "\n"
                    self._widgets = self._widgets[1:]
                if remaining_width < 10 * flex_count:
                    self._file_mode = True
                    remaining_width = 10 * flex_count

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
            return 80

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
        return int(os.environ.get("COLUMNS", 80))



#-----------------------------------------------------------------------------------------------------------------------
# Widgets (implementations)
#-----------------------------------------------------------------------------------------------------------------------

class PBWString(ProgressBarWidget):
    """Widget that represents a fixed string."""

    def __init__(self, text):
        """Initialize the widget."""
        super(ProgressBarWidget, self).__init__()
        self._str = "%s" % text

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

    def render(self, progress, width=None, status=None):
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
                out += colorama.Fore.RED + " (" + status + ")" + colorama.Style.RESET_ALL
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
            except UnicodeEncodeError:
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


# Initialize colorama
colorama.init()
