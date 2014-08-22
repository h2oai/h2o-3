# H2O System Requirements

64-bit Java 1.6 or higher (Java 1.7 is fine, for example)

While a minimum of 2g ram is needed on the machine where H2O will be running, the amount of memory needed for H2O to
run efficiently is dependent on the size and nature of data, and the
algorithm employed. A good heuristic is that the amount of memory
available be at least four times the size of the data being analyzed.

A reasonably modern web browser (for example, the latest version of
Firefox, Safari or IE.)

Users who are running H2O on a server must ensure that the data are
available to that server (either via their network settings, or
because the data are on the same server.) Users who are running H2O on a laptop must ensure that the data are available to
that laptop. The specification of network settings is beyond the scope
of this documentation. Advanced users may find additional documentation on
running in specialized environments helpful: see [Developer Guide](../develop/index)

For multinode clusters utilizing several servers, it is strongly
recommended that all servers and nodes be symmetric and identically
configured. For example, allocating different amounts of memory to
nodes in the same cluster can adversely impact performance.


