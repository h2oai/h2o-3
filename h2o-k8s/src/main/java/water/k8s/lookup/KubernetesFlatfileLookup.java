package water.k8s.lookup;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class KubernetesFlatfileLookup extends RouterNanoHTTPD.DefaultHandler implements KubernetesLookup {

    private final Logger LOG = Logger.getLogger(KubernetesFlatfileLookup.class);
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile static Optional<Set<String>> podIps = Optional.empty();

    @Override
    public Optional<Set<String>> lookupNodes(final Collection<LookupConstraint> lookupConstraints) {
        final Lock lock = KubernetesFlatfileLookup.lock.readLock();
        try {
            return podIps;
        } finally {
            lock.unlock();
            // Once the pod IPs were received and successfully sent, remove the variable's content to make sure the memory is freed.
            if (podIps.isPresent()) {
                podIps = null;
            }
        }
    }

    @Override
    public String getText() {
        throw new IllegalStateException(String.format("Method getText should not be called on '%s'",
                getClass().getName()));
    }

    @Override
    public String getMimeType() {
        return "text/plain";
    }

    @Override
    public NanoHTTPD.Response.IStatus getStatus() {
        return null;
    }

    public static final String RESPONSE_MIME_TYPE = "text/plain";

    @Override
    public NanoHTTPD.Response post(final RouterNanoHTTPD.UriResource uriResource, final Map<String, String> urlParams, final NanoHTTPD.IHTTPSession session) {
        final Map<String, String> map = new HashMap<>();
        try {
            session.parseBody(map);
        } catch (IOException | NanoHTTPD.ResponseException e) {
            LOG.error("Received incorrect Kubernetes flatfile request.", e);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, RESPONSE_MIME_TYPE, null);
        }
        final String postBody = map.get("postData");
        final Optional<Set<String>> ips = parseH2ONodesIPs(postBody);

        if (ips.isPresent()) {
            final Lock writeLock = KubernetesFlatfileLookup.lock.writeLock();
            try {
                writeLock.lock();
                if (podIps.isPresent()) {
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, RESPONSE_MIME_TYPE,
                            "IP Addresses already provided. H2O is started.");
                } else {
                    podIps = ips;
                }
            } finally {
                writeLock.unlock();
            }
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, RESPONSE_MIME_TYPE, "Pod IPs parsed. H2O is starting.");
        } else {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, RESPONSE_MIME_TYPE,
                    "Unable to parse IP addresses in body. Only one IPv4/IPv6 address per line is accepted.");
        }

    }


    public Optional<Set<String>> parseH2ONodesIPs(final String flatfile) {
        // Matches both IPv4, IPv6 and IPv6 compressed, as in Kubernetes, IPv6 pod adresses are possible since K8S API v16.
        final String[] lines = flatfile.split("\n");
        Set<String> ips = Arrays.stream(lines)
                .map(line -> {
                    try {
                        // InetAddress parses both Ipv4 and Ipv6 addresses
                        // The address is then converted back to String to make sure the output is unified (IPv6)
                        return InetAddress.getByName(line).toString();
                    } catch (UnknownHostException e) {
                        return null;
                    }
                }).filter(ip -> ip != null)
                .collect(Collectors.toSet());

        if (ips.size() != lines.length) {
            return Optional.empty();
        } else {
            return Optional.of(ips);
        }
    }
}
