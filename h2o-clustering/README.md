# H2O Assisted clustering

Assisted clustering is a special regime of H2O startup, invoked by setting the `H2O_ASSISTED_CLUSTERING_REST` environment
variable to `True`. In this regime, `AssistedClusteringEmbeddedConfigProvider` is activated and spawns an HTTP endpoint at
`http://h2o-node-host:<port:default 8080>/clustering/flatfile`, expecting a `POST` HTTP request with `Content-type: text/plain`
and a standard H2O flatfile in the request body.

### Example
```
curl --location --request POST 'localhost:8080/clustering/flatfile' \
--header 'Content-Type: text/plain' \
--data-raw '255.255.255.0:54321'
```

## Flatfile format

This module itself does not make any assumptions about the flatfile formatting, besides the basic ones like
an empty or missing request body. The flatfile is handed over to H2O and parsed by `NetworkInit.java` - which contains all the details.
IPv4 and IPv6 formats are supported (not the shortened representation of IPv6). Both hostname/host ip and port in a format
`hostname:port` must be present.

### Example

A flatfile with an IPv6 and IPv4 address with ports defined in both cases (mandatory).
```
[1200:0000:AB00:1234:0000:2552:7777:1313]:54321
9.255.255.255:54321
```

## Distribution

It is assumed this module is NOT part of H2O by default. It must be put onto the classpath manually. As it only relies on the contract of
`AbstractEmbeddedH2OConfig`, which is considered to be fixed, it is possible to put this module on a classpath
of even older H2Os which contain `AbstractEmbeddedH2OConfig`. This enables assisted clustering even to legacy
H2O versions.


## Testing

This module, besides its own test suite, is tested in Kubernetes environment inside the [h2o-k8s](../h2o-k8s/tests/clustering/README.md)
module.

## Cluster status endpoint

There is an endpoint for H2O Cluster status provided by this module, located via `GET /cluster/status`. 
If H2O has not clustered yet, the endpoint `HTTP 204` - No content, according to [RFC-2616](https://tools.ietf.org/html/rfc2616#section-10.2.5).
Once H2O cluster is formed, this endpoint returns `HTTP 200` with a JSON containing two arrays - a list of healthy nodes and a list of
unhealthy nodes.

Any node of the cluster can be queried and each will return the same response.


Example `curl --location --request GET 'localhost:8080/cluster/status'`:

```json
{
  "leader_node": "192.168.0.149:54321",
  "healthy_nodes": ["192.168.0.149:54321"],
  "unhealthy_nodes": []
}
```
