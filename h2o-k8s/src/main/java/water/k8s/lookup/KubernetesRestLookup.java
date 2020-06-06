package water.k8s.lookup;

import com.google.common.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Watch;
import org.slf4j.Logger;
import water.util.Log;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class KubernetesRestLookup implements KubernetesLookup{

  @Override
  public Optional<Set<String>> lookupNodes(Collection<LookupConstraint> lookupConstraints) {

    try {
      final ApiClient client = ClientBuilder.cluster().build();
      Configuration.setDefaultApiClient(client);
      final CoreV1Api api = new CoreV1Api();
      final String label = "h2o-k8s";

      Watch<V1Pod> watch =
          Watch.createWatch(
              client,
              api.listNamespacedPodCall("default", null, false, null, null, label, 10, null, null, false, null),
              new TypeToken<Watch.Response<V1Pod>>() {}.getType());
      final Set<String> addresses = new HashSet<>();
      try {
        for (Watch.Response<V1Pod> item : watch) {
          System.out.printf("%s", item.object.getStatus().getPodIP());
          addresses.add(item.object.getStatus().getPodIP());
        }
      } finally {
        watch.close();
      }

      return Optional.of(addresses);
    } catch (IOException | ApiException e) {
      Log.err(e);
      return Optional.empty();
    }
  }



}
