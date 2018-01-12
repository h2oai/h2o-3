package hex.tree.xgboost.rabit;

import hex.tree.xgboost.rabit.util.LinkMap;
import ml.dmlc.xgboost4j.java.IRabitTracker;
import water.*;
import water.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class RabitTrackerH2O extends Thread implements IRabitTracker {
    public static final int MAGIC = 0xff99;
    private ServerSocketChannel sock;
    private int port = 9091;

    private int workers;

    private transient Map<String, String> envs = new HashMap<>();
    private LinkMap linkMap;
    private Map<String, Integer> jobToRankMap = new HashMap<>();

    public RabitTrackerH2O(int workers) throws IOException {
        super();
        setPriority(MAX_PRIORITY-1);

        if(workers < 1) {
            throw new IllegalStateException("workers must be greater than or equal to one (1).");
        }

        this.workers = workers;

        boolean tryToBind = true;
        while(tryToBind) {
            try {
                this.sock = ServerSocketChannel.open();
                this.sock.socket().setReceiveBufferSize(64 * 1024);
                InetSocketAddress isa = new InetSocketAddress(H2O.SELF_ADDRESS, this.port);
                this.sock.socket().bind(isa);
                tryToBind = false;
            } catch (java.io.IOException e) {
                this.port++;
                if(this.port > 9999) {
                    throw e;
                }
            }
        }

        this.setName("TCP-" + sock);

        envs.put("DMLC_NUM_WORKER", String.valueOf(workers));
        envs.put("DMLC_NUM_SERVER", "0");
        envs.put("DMLC_TRACKER_URI", H2O.SELF_ADDRESS.getHostAddress());
        envs.put("DMLC_TRACKER_PORT", Integer.toString(port));
        envs.put("rabit_world_size", Integer.toString(workers));

        Log.info("Rabit tracker started on port ", this.port);
    }

    @Override
    public Map<String, String> getWorkerEnvs() {
        return envs;
    }

    @Override
    public boolean start(long timeout) {
        this.setDaemon(true);
        this.start();
        return true;
    }

    @Override
    public void run() {
        Set<Integer> shutdown = new HashSet<>();
        Map<Integer, RabitWorker> waitConn = new HashMap<>();
        List<RabitWorker> pending = new ArrayList<>();
        Queue<Integer> todoNodes = new ArrayDeque<>(workers);
        while(shutdown.size() != workers) {
            try {
                SocketChannel channel = this.sock.accept();
                RabitWorker worker = new RabitWorker(channel);

                if("print".equals(worker.cmd)) {
                    String msg = worker.receiver().getStr();
                    Log.warn("Rabit worker: ", msg);
                    continue;
                } else if ("shutdown".equals(worker.cmd)) {
                    assert worker.rank >= 0 && !shutdown.contains(worker.rank);
                    assert !waitConn.containsKey(worker);
                    shutdown.add(worker.rank);
                    Log.debug("Received ", worker.cmd, " signal from ", worker.rank);
                    continue;
                }
                assert "start".equals(worker.cmd) || "recover".equals(worker.cmd);

                if("recover".equals(worker.cmd)) {
                    assert worker.rank >= 0;
                }

                if(null == linkMap) {
                    linkMap = new LinkMap(workers);
                    for(int i = 0; i < workers; i++) {
                        todoNodes.add(i);
                    }
                } else {
                    assert worker.worldSize == -1 || worker.worldSize == workers;
                }

                if("recover".equals(worker.cmd)) {
                    assert worker.rank >= 0;
                }

                int rank = worker.decideRank(jobToRankMap);
                if(-1 == rank) {
                    assert todoNodes.size() != 0;
                    pending.add(worker);
                    if(pending.size() == todoNodes.size()) {
                        Collections.sort(pending);
                        for (RabitWorker p : pending) {
                            rank = todoNodes.poll();
                            if(!"NULL".equals(p.jobId)) {
                                jobToRankMap.put(worker.jobId, rank);
                            }
                            worker.assignRank(rank, waitConn, linkMap);

                            if(worker.waitAccept > 0) {
                                waitConn.put(rank, worker);
                            }

                            Log.debug("Received " + worker.cmd +
                                    " signal from " + worker.host +
                                    ". Assigned rank " + worker.rank
                            );
                        }
                    }
                    if(todoNodes.isEmpty()) {
                        Log.debug("All " + workers + " Rabit workers are getting started.");
                    }
                } else {
                    worker.assignRank(rank, waitConn, linkMap);
                    if(worker.waitAccept > 0) {
                        waitConn.put(rank, worker);
                    }
                }
        } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.debug("All Rabit nodes finished.");
    }

    @Override
    public int waitFor(long timeout) {
        throw new UnsupportedOperationException("Wait is not supported by H2O tracker. Synchronization should be done using H2O MR framework.");
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        throw new UnsupportedOperationException("Uncaught exception is not supported by H2O tracker.");
    }
}
