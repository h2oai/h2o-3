package hex.tree.xgboost.rabit;

import hex.tree.xgboost.rabit.communication.XGBoostAutoBuffer;
import hex.tree.xgboost.rabit.util.LinkMap;
import org.apache.log4j.Logger;
import water.AutoBuffer;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RabitWorker implements Comparable<RabitWorker> {

    private static final Logger LOG = Logger.getLogger(RabitWorker.class);

    final String host;
    final int workerPort;
    private SocketChannel socket;
    int rank;
    int worldSize;
    String jobId;
    public String cmd;
    int waitAccept;
    private int port;
    private XGBoostAutoBuffer ab;
    private XGBoostAutoBuffer writerAB;


    RabitWorker(SocketChannel channel) throws IOException {
        this.ab = new XGBoostAutoBuffer(channel);
        this.socket = channel;
        this.host = channel.socket().getInetAddress().getHostAddress();
        this.workerPort = channel.socket().getPort();
        int magicReceived = ab.get4();
        if(RabitTrackerH2O.MAGIC != magicReceived) {
            throw new IllegalStateException(
                    "Tracker received wrong magic number ["
                    + magicReceived +
                    "] from host " + this.host
            );
        }

        writerAB = new XGBoostAutoBuffer();
        writerAB.put4(RabitTrackerH2O.MAGIC);
        AutoBuffer.writeToChannel(writerAB.buffer(), socket);

        this.rank = ab.get4();
        this.worldSize = ab.get4();
        this.jobId = safeLowercase(ab.getStr());
        this.cmd = safeLowercase(ab.getStr());
        this.waitAccept = 0;
        this.port = -1;
        LOG.debug("Initialized worker " + this.host + " with rank " + this.rank + " and command [" + this.cmd + "].");
    }

    private String safeLowercase(String str) {
        return null == str ? null : str.toLowerCase();
    }

    int decideRank(Map<String, Integer> jobToRankMap) {
        if (rank >= 0) {
            return rank;
        }
        if (!"null".equals(jobId) && jobToRankMap.containsKey(jobId)) {
            return jobToRankMap.get(jobId);
        }
        return -1;
    }

    public XGBoostAutoBuffer receiver() {
        return ab;
    }

    public void assignRank(int rank, Map<Integer, RabitWorker> waitConn, LinkMap linkMap) throws IOException {
        this.rank = rank;
        List<Integer> nnset = linkMap.treeMap.get(rank);
        Integer rprev = linkMap.ringMap.get(rank)._1();
        Integer rnext = linkMap.ringMap.get(rank)._2();

        writerAB.put4(rank);
        writerAB.put4(linkMap.parentMap.get(rank));
        writerAB.put4(linkMap.treeMap.size());
        writerAB.put4(nnset.size());

        for (Integer r : nnset) {
            writerAB.put4(r);
        }

        if (rprev != -1 && rprev != rank) {
            nnset.add(rprev);
            writerAB.put4(rprev);
        } else {
            writerAB.put4(-1);
        }

        if (rnext != -1 && rnext != rank) {
            nnset.add(rnext);
            writerAB.put4(rnext);
        } else {
            writerAB.put4(-1);
        }
        AutoBuffer.writeToChannel(writerAB.buffer(), socket);

        while (true) {
            int ngood = ab.get4();
            Set<Integer> goodSet = new LinkedHashSet<>();
            for(int i = 0; i < ngood; i++) {
                int got = ab.get4();
                goodSet.add(got);
            }
            assert nnset.containsAll(goodSet);
            Set<Integer> badSet = new LinkedHashSet<>(nnset);
            badSet.removeAll(goodSet);
            Set<Integer> conset = new LinkedHashSet<>();
            for (Integer r : badSet) {
                if(waitConn.containsKey(r)) {
                    conset.add(r);
                }
            }

            writerAB.put4(conset.size());
            AutoBuffer.writeToChannel(writerAB.buffer(), socket);
            writerAB.put4(badSet.size() - conset.size());
            AutoBuffer.writeToChannel(writerAB.buffer(), socket);

            for (Integer r : conset) {
                writerAB.putStr(waitConn.get(r).host);
                writerAB.put4(waitConn.get(r).port);
                writerAB.put4(r);
                AutoBuffer.writeToChannel(writerAB.buffer(), socket);
            }

            int nerr = ab.get4();
            if(nerr != 0) {
                continue;
            }
            this.port = ab.get4();
            Set<Integer> rmset = new LinkedHashSet<>();
            // All connections were successfully setup
            for (Integer r : conset) {
                waitConn.get(r).waitAccept -= 1;
                if(waitConn.get(r).waitAccept == 0) {
                    rmset.add(r);
                }
            }
            for (Integer r : rmset) {
                waitConn.remove(r);
            }
            this.waitAccept = badSet.size() - conset.size();
            return;
        }
    }

    @Override
    public int compareTo(RabitWorker o) {
        return jobId.compareTo(o.jobId);
    }
}
