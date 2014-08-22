# High Availability Considerations for H2O

It's helpful to think about high availability needs based on different
stages in your workflow.

## Data exploration (Interactive)

This is an interactive process that is generally carried out either
through the R language (H2O + R package) or the H2O Web UI. In the current version of (H2O, the
cluster itself does not have HA support.  Users needing HA can,
however, connect to a spare H2O cluster by providing a
different IP address and Port to connect to.

## Modeling (Interactive or batch)

Once again, since the current version of H2O does not have
specific HA support, users that have HA requirements can keep spare
clusters available and point to them via an IP address and Port.

Hadoop users in particular may wish to create a disposable cluster for
each individual modeling job and discard the cluster upon completion.
The 'hadoop jar' command makes it very easy to spin up a cluster on
demand.  This approach limits the loss of a cluster to an individual
job, which can be restarted by a user script.


## Predicting (Real-time)

For real-time predictions, the H2O models can be exported
into plain Java POJO classes.  These plain Java objects just do basic
arithmetic operations and don't allocate any data.  Since they run
independently from the full H2O software stack, they don't
add any additional HA or uptime complexity to a production
environment.

