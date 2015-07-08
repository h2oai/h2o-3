# H2O Life cycle

---

## H2OApp vs. H2OClientApp

There are two main classes to start an H2O.

H2OApp starts a worker H2O node that participates as a full DKV member and can execute work.  This is the normal way to start H2O.  This is how standalone H2O and all the nodes in H2O on Hadoop work.

H2OClientApp starts an observer H2O client node that does not execute work.  This is used by the one driver node in Sparkling Water (the remaining worker executor nodes are all regular H2OApp nodes).

#### CAUTION

If you just call H2O.main() directly by itself, the REST API won't get registered and REST API requests will never be served.  You probably meant to call H2OApp.main() instead!  This is a common mistake to make.

## Embedding H2O

One way to Embed H2O is to use water.init.AbstractEmbeddedH2OConfig.  H2O on Hadoop uses this.   The EmbeddedH2OConfig registers a callback to assist with gathering the IP address and Port of individual H2O nodes and distributing them.

See <https://github.com/h2oai/h2o-3/blob/master/h2o-hadoop/h2o-mapreduce-generic/src/main/java/water/hadoop/h2omapper.java>

## Startup

The main class for Standalone H2O is H2OApp.

See <https://github.com/h2oai/h2o-3/blob/master/h2o-app/src/main/java/water/H2OApp.java>

The overall flow is shown below:

```
registerExtensions();
H2O.main(args);
registerRestApis(relativeResourcePath);
H2O.finalizeRegistration();
```
The call to registerExtensions hooks in any H2O extensions found on the classpath.

The call to H2O.main() allocates ports, prepares the web server, and does all kinds of other startup work.

The call to registerRestApis() add REST API routes for their respective subsystems.  (water is from h2o-core and hex is from h2o-algos.)

The call to H2O.finalizeRegistration() signals that all routes have been added and tells the in-H2O web server to start accepting REST API requests.

H2O cloud formation can occur even after H2O.finalizeRegistration.  New H2O nodes are allowed to join until the cloud receives a piece of work to do.  Usually this means until the cloud receives a REST API request or writes to the DKV.

## Shutdown

There are various ways to shut H2O down, as shown below.  H2O does not support graceful in-process.  You need to exit the process.

### Java / Scala

H2O.exit(0)

### REST API

/3/Shutdown

### R

h2o.shutdown()
