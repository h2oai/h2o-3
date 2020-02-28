package water.k8s;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import water.k8s.lookup.LookupConstraint;

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

public class KubernetesDnsDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesEmbeddedConfig.class);

    private static final String H2O_K8S_SERVICE_DNS_DEFAULT_NAME = "H2O_KUBERNETES_SERVICE_DNS";
    private static final String DNS_TIMEOUT_DEFAULT = "30000"; // 30 seconds
    private static final int ONE_SECOND = 1000;
    private final String serviceDns;
    private final DirContext dirContext;

    public KubernetesDnsDiscovery(final String serviceDns) {
        this.serviceDns = serviceDns;
        this.dirContext = initDirContext();
    }

    /**
     * @return
     * @throws IllegalStateException When the H2O-related kubernetes DNS service is not found
     */
    public static KubernetesDnsDiscovery fromH2ODefaults() throws IllegalStateException {
        final String dnsServiceName = System.getenv(H2O_K8S_SERVICE_DNS_DEFAULT_NAME);
        if (dnsServiceName == null) {
            throw new IllegalStateException(String.format("DNS of H2O service not set. Please set the '%s' variable.",
                    H2O_K8S_SERVICE_DNS_DEFAULT_NAME));
        } else if (dnsServiceName.trim().isEmpty()) {
            throw new IllegalStateException(String.format("DNS Service '%s' name is invalid.", dnsServiceName));
        }
        return new KubernetesDnsDiscovery(dnsServiceName);
    }

    private static String extractHost(String server) {
        String host = server.split(" ")[3];
        return host.replaceAll("\\\\.$", "");
    }

    public Optional<Set<String>> lookupNodes(final Collection<LookupConstraint> lookupStrategies) {
        final Set<String> lookedUpNodes = new HashSet<>();

        while (lookupStrategies.stream().allMatch(lookupStrategy -> !lookupStrategy.isLookupEnded(lookedUpNodes))) {
            try {
                lookup(lookedUpNodes);
                Thread.sleep(ONE_SECOND);
            } catch (NamingException e) {
                continue;
            } catch (InterruptedException e) {
                return Optional.empty();
            }
        }

        return Optional.of(lookedUpNodes);
    }

    private void lookup(final Set<String> nodeIPs) throws NamingException {
        final Attributes attributes = dirContext.getAttributes(serviceDns, new String[]{"SRV"});
        final Attribute srvAttribute = attributes.get("srv");
        if (srvAttribute != null) {
            final NamingEnumeration<?> servers = srvAttribute.getAll();
            while (servers.hasMore()) {
                final String server = (String) servers.next();
                final String serverHost = extractHost(server);
                final InetAddress nodeIP;
                try {
                    nodeIP = InetAddress.getByName(serverHost);
                } catch (UnknownHostException e) {
                    LOGGER.error("Unknown host for IP Address: " + serverHost);
                    continue;
                }
                if (nodeIPs.add(nodeIP.getHostAddress())) {
                    LOGGER.info(String.format("New H2O pod with DNS record '%s' discovered.", nodeIP));
                }
            }
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
