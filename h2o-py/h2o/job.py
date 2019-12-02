# -*- encoding: utf-8 -*-
"""
Handler to an asynchronous task executed on the remote server.

A job is an object with states: CREATED, RUNNING, DONE, FAILED, CANCELLED
A job can be polled for completion and reports the current progress as long as it is RUNNING.
"""
from __future__ import division, print_function, absolute_import, unicode_literals

import functools as ft
import warnings

import h2o
from h2o.exceptions import H2OJobCancelled
from h2o.utils.progressbar import ProgressBar
from h2o.utils.shared_utils import clamp


class H2OJob(object):
    """A class representing an H2O Job."""

    __PROGRESS_BAR__ = True  # display & update progress bar while polling

    def __init__(self, jobs, job_type):
        """Initialize new H2OJob object."""
        if "jobs" in jobs:
            job = jobs["jobs"][0]
        elif "job" in jobs:
            job = jobs["job"]
        else:
            job = jobs

        self.job = job
        self.status = job["status"]
        self.job_key = job["key"]["name"]
        self.dest_key = job["dest"]["name"]
        self.warnings = None
        self.progress = 0
        self.exception = job["exception"] if "exception" in job else None
        self._job_type = job_type
        self._polling = False
        self._poll_count = 10**10


    def poll(self, poll_updates=None):
        """
        Wait until the job finishes.

        This method will continuously query the server about the status of the job, until the job reaches a
        completion. During this time we will display (in stdout) a progress bar with % completion status.
        :param poll_updates: a callback function called a each polling iteration with 2 arguments:
            (current_job: H2OJob, bar_progression: float)
        """
        try:
            hidden = not H2OJob.__PROGRESS_BAR__
            pb = ProgressBar(title=self._job_type + " progress", hidden=hidden)
            if poll_updates:
                pb.execute(self._refresh_job_status, print_verbose_info=ft.partial(poll_updates, self))
            else:
                pb.execute(self._refresh_job_status)
        except StopIteration as e:
            if str(e) == "cancelled":
                self.cancel()
            # Potentially we may want to re-raise the exception here

        assert self.status in {"DONE", "CANCELLED", "FAILED"} or self._poll_count <= 0, \
            "Polling finished while the job has status %s" % self.status
        if self.warnings:
            for w in self.warnings:
                warnings.warn(w)

        # check if failed... and politely print relevant message
        if self.status == "CANCELLED":
            raise H2OJobCancelled("Job<%s> was cancelled by the user." % self.job_key)
        if self.status == "FAILED":
            if (isinstance(self.job, dict)) and ("stacktrace" in list(self.job)):
                raise EnvironmentError("Job with key {} failed with an exception: {}\nstacktrace: "
                                       "\n{}".format(self.job_key, self.exception, self.job["stacktrace"]))
            else:
                raise EnvironmentError("Job with key %s failed with an exception: %s" % (self.job_key, self.exception))

        return self

    # TODO: this is not multi-client safe:
    def poll_once(self):
        """Query the job status and show the progress bar, but then cancel immediately."""
        self._poll_count = 1
        self.poll()
        self._poll_count = 10**10
        return self

    def cancel(self):
        h2o.api("POST /3/Jobs/%s/cancel" % self.job_key)
        self.status = "CANCELLED"

    def _refresh_job_status(self):
        if self._poll_count <= 0: raise StopIteration("")
        jobs = h2o.api("GET /3/Jobs/%s" % self.job_key)
        self.job = jobs["jobs"][0] if "jobs" in jobs else jobs["job"][0]
        self.status = self.job["status"]
        self.progress = self.job["progress"]
        self.exception = self.job["exception"]
        self.warnings = self.job["warnings"] if "warnings" in self.job else None
        self._poll_count -= 1
        # Sometimes the server may report the job at 100% but still having status "RUNNING" -- we work around this
        # by showing progress at 99% instead. Sometimes the server may report the job at 0% but having status "DONE",
        # in this case we set the progress to 100% manually.
        if self.status == "CREATED": self.progress = 0
        if self.status == "RUNNING": self.progress = clamp(self.progress, 0, 0.99)
        if self.status == "DONE": self.progress = 1
        if self.status == "FAILED": raise StopIteration("failed")
        if self.status == "CANCELLED": raise StopIteration("cancelled by the server")
        return self.progress

    def __repr__(self):
        if self.status in {"CREATED", "RUNNING"}:
            desc = "at %d%%" % int(self.progress * 100 + 0.5)
        else:
            desc = self.status.lower()
        return "<H2OJob id=%s %s>" % (self.job_key, desc)
