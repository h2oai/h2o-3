package water.k8s.lookup;


import water.util.Log;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Discovery strategy on a DNS cluster leveraging the DNS record of a Kubernetes headless service present in the cluster.
 * Kubernetes headless services, instead of load-balancing the requests onto one of the underlying pods, return the
 * addresses of all the pods covered by the headless service.
 * <p>
 * Such pods can then be discovered via the above-mentioned DNS record. In order for H2O to know which service to query,
 * it is mandatory for the K8S user to pass the name of the headless service to the H2O container, as follows:
 *
 * <pre>
 * apiVersion: apps/v1
 * kind: StatefulSet
 * metadata:
 *   name: h2o-stateful-set
 *   namespace: h2o-statefulset
 * spec:
 *   serviceName: h2o-service
 *   replicas: 3
 *   selector:
 *     matchLabels:
 *       app: h2o-k8s
 *   template:
 *     metadata:
 *       labels:
 *         app: h2o-k8s
 *     spec:
 *       terminationGracePeriodSeconds: 10
 *       containers:
 *         - name: h2o-k8s
 *           image: '<someDockerImageWithH2OInside>'
 *           resources:
 *             requests:
 *               memory: "4Gi"
 *           ports:
 *             - containerPort: 54321
 *               protocol: TCP
 *           env:
 *           - name: H2O_KUBERNETES_SERVICE_DNS
 *             value: h2o-service.h2o-statefulset.svc.cluster.local
 *           - name: H2O_NODE_LOOKUP_TIMEOUT
 *             value: '180'
 *           - name: H2O_NODE_EXPECTED_COUNT
 *             value: '3'
 * </pre>
 */
public class KubernetesDnsLookup implements KubernetesLookup {

    private static final String K8S_SERVICE_DNS_ENV_VAR_KEY = "H2O_KUBERNETES_SERVICE_DNS";
    private static final String DNS_TIMEOUT_DEFAULT = "30000"; // 30 seconds
    private static final int ONE_SECOND = 1000;
    private final String serviceDns;
    private final DirContext dirContext;

    public KubernetesDnsLookup(final String serviceDns) {
        this.serviceDns = serviceDns;
        this.dirContext = initDirContext();
    }

    /**
     * @return
     * @throws IllegalStateException When the H2O-related kubernetes DNS service is not found
     */
    public static KubernetesDnsLookup fromH2ODefaults() throws IllegalStateException {
        final String dnsServiceName = System.getenv(K8S_SERVICE_DNS_ENV_VAR_KEY);
        if (dnsServiceName == null) {
            throw new IllegalStateException(String.format("DNS of H2O service not set. Please set the '%s' variable.",
                    K8S_SERVICE_DNS_ENV_VAR_KEY));
        } else if (dnsServiceName.trim().isEmpty()) {
            throw new IllegalStateException(String.format("DNS Service '%s' name is invalid.", dnsServiceName));
        }
        return new KubernetesDnsLookup(dnsServiceName);
    }

    private static String extractHost(final String server, final Pattern extractHostPattern) {
        String host = server.split(" ")[3];
        return extractHostPattern.matcher(host).replaceAll("");
    }

    /**
     * Looks up H2O pods via configured K8S Stateless service DNS. Environment variable with key defined in
     * H2O_K8S_SERVICE_DNS_KEY constant is used to obtain address of the DNS. The DNS is then queried for pods
     * in the underlying service. It is the responsibility of the K8S cluster owner to set-up the service correctly to
     * only provide correct adresses of pods with H2O active. If pods with no H2O running are supplied, the resulting
     * flatfile may contain pod IPs with no H2O running as well.
     *
     * @param lookupConstraints Constraints to obey during lookup
     * @return A {@link Set} of adresses of looked up nodes represented as String. The resulting set is never empty.
     */
    public Optional<Set<String>> lookupNodes(final Collection<LookupConstraint> lookupConstraints) {
        final Set<String> lookedUpNodes = new HashSet<>();

        while (lookupConstraints.stream().allMatch(lookupStrategy -> !lookupStrategy.isLookupEnded(lookedUpNodes))) {
            try {
                dnsLookup(lookedUpNodes);
            } catch (NamingException e) {
                Log.warn(e.getMessage());
                continue;
            } finally {
                try {
                    Thread.sleep(ONE_SECOND);
                } catch (InterruptedException e) {
                    Log.err(e);
                    return Optional.empty();
                }
            }
        }

        return Optional.of(lookedUpNodes);
    }

    public static boolean isLookupPossible() {
        return System.getenv()
                .containsKey(KubernetesDnsLookup.K8S_SERVICE_DNS_ENV_VAR_KEY);
    }

    /**
     * Performs a single DNS lookup. Discovered nodes (their IPs respectively) are addded to the existing
     * set of nodeIPs.
     *
     * @param nodeIPs A {@link Set} of nodes already discovered during previous lookups.
     * @throws NamingException If the DNS under given name is unreachable / does not exist.
     */
    private void dnsLookup(final Set<String> nodeIPs) throws NamingException {
        final Attributes attributes = dirContext.getAttributes(serviceDns, new String[]{"SRV"});
        final Attribute srvAttribute = attributes.get("srv");
        final Pattern extractHostPattern = Pattern.compile("\\\\.$");
        if (srvAttribute != null) {
            final NamingEnumeration<?> servers = srvAttribute.getAll();
            while (servers.hasMore()) {
                final String server = (String) servers.next();
                final String serverHost = extractHost(server, extractHostPattern);
                final InetAddress nodeIP;
                try {
                    nodeIP = InetAddress.getByName(serverHost);
                } catch (UnknownHostException e) {
                    Log.err("Unknown host for IP Address: " + serverHost);
                    continue;
                }
                if (nodeIPs.add(nodeIP.getHostAddress())) {
                    Log.info(String.format("New H2O pod with DNS record '%s' discovered.", nodeIP));
                }
            }
            servers.close();
        }
    }

    private DirContext initDirContext() {
        final Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        environment.put(Context.PROVIDER_URL, "dns:");
        environment.put("com.sun.jndi.dns.timeout.initial", DNS_TIMEOUT_DEFAULT);
        try {
            return new InitialDirContext(environment);
        } catch (NamingException e) {
            throw new IllegalStateException("Error while initializing DirContext", e);
        }
    }
}
