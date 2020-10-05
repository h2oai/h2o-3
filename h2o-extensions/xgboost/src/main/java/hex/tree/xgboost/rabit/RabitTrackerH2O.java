package hex.tree.xgboost.rabit;

import hex.tree.xgboost.rabit.util.LinkMap;
import ai.h2o.xgboost4j.java.IRabitTracker;
import org.apache.log4j.Logger;
import water.H2O;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class RabitTrackerH2O implements IRabitTracker {

    private static final Logger LOG = Logger.getLogger(RabitTrackerH2O.class);

    public static final int MAGIC = 0xff99;

    private ServerSocketChannel sock;
    private int port = 9091;

    private final int workers;

    private final Map<String, String> envs = new HashMap<>();

    private volatile RabitTrackerH2OThread trackerThread;

    public RabitTrackerH2O(int workers) {
        if (workers < 1) {
            throw new IllegalStateException("workers must be greater than or equal to one (1).");
        }
        this.workers = workers;
    }

    @Override
    public Map<String, String> getWorkerEnvs() {
        envs.put("DMLC_NUM_WORKER", String.valueOf(workers));
        envs.put("DMLC_NUM_SERVER", "0");
        envs.put("DMLC_TRACKER_URI", H2O.SELF_ADDRESS.getHostAddress());
        envs.put("DMLC_TRACKER_PORT", Integer.toString(port));
        envs.put("DMLC_TASK_ID", Integer.toString(H2O.SELF.index()));
        envs.put("DMLC_WORKER_STOP_PROCESS_ON_ERROR", "false");
        envs.put("rabit_world_size", Integer.toString(workers));
        return envs;
    }

    @Override
    public boolean start(long timeout) {
        boolean tryToBind = true;
        while (tryToBind) {
            try {
                this.sock = ServerSocketChannel.open();
                this.sock.socket().setReceiveBufferSize(64 * 1024);
                InetSocketAddress isa = new InetSocketAddress(H2O.SELF_ADDRESS, this.port);
                this.sock.socket().bind(isa);
                LOG.debug("Started listening on port " + this.port);
                tryToBind = false;
            } catch (java.io.IOException e) {
                this.port++;
                try {
                    this.sock.close();
                } catch (IOException socketCloseException) {
                    LOG.warn("Failed to close Rabit Tracker socket on port " + sock.socket().getLocalPort());
                }
                if (this.port > 9999) {
                    throw new RuntimeException("Failed to bind Rabit tracker to a socket in range 9091-9999", e);
                }
            }
        }

        if (null != this.trackerThread) {
            throw new IllegalStateException("Rabit tracker already started.");
        }
        this.trackerThread = new RabitTrackerH2OThread(this);
        this.trackerThread.start();
        return true;
    }

    @Override
    public void stop() {
        assert this.trackerThread != null;
        try {
            this.trackerThread.interrupt();
        } catch (SecurityException e){
            LOG.error("Could not interrupt a thread in RabitTrackerH2O: " + trackerThread.toString());
        }
        this.trackerThread.terminateSocketChannels();
        this.trackerThread = null;


        try {
            this.sock.close();
            this.port = 9091;
        } catch (IOException e) {
            LOG.error("Failed to close Rabit tracker socket.", e);
        }
    }

    private static class RabitTrackerH2OThread extends Thread {

        private final RabitTrackerH2O tracker;

        private final Map<String, Integer> jobToRankMap = new HashMap<>();
        private final List<SocketChannel> socketChannels = new ArrayList<>();

        private RabitTrackerH2OThread(RabitTrackerH2O tracker) {
            setPriority(MAX_PRIORITY-1);
            this.setName("TCP-" + tracker.sock);
            this.tracker = tracker;
        }

        private void terminateSocketChannels(){
            for (SocketChannel channel : socketChannels) {
                try {
                    channel.close();
                } catch (IOException e) {
                    LOG.warn("Unable to close RabitTracerH2O SocketChannel on port " + channel.socket().getPort());
                }
            }
        }

        private static final String PRINT_CMD = "print";
        private static final String SHUTDOWN_CMD = "shutdown";
        private static final String START_CMD = "start";
        private static final String RECOVER_CMD = "recover";
        private static final String NULL_STR = "null";

        @Override
        public void run() {
            LOG.debug("Rabit tracker started on port " + tracker.port);
            LinkMap linkMap = null;
            Set<Integer> shutdown = new HashSet<>();
            Map<Integer, RabitWorker> waitConn = new HashMap<>();
            List<RabitWorker> pending = new ArrayList<>();
            Queue<Integer> todoNodes = new ArrayDeque<>(tracker.workers);
            while (!interrupted() && shutdown.size() != tracker.workers) {
                try{
                    final SocketChannel channel = tracker.sock.accept(); // Does not proceed when interrupt() is called.
                    socketChannels.add(channel);
                    final RabitWorker worker = new RabitWorker(channel);

                    if (PRINT_CMD.equals(worker.cmd)) {
                        String msg = worker.receiver().getStr();
                        LOG.warn("Rabit worker: " + msg);
                        continue;
                    } else if (SHUTDOWN_CMD.equals(worker.cmd)) {
                        assert worker.rank >= 0 && !shutdown.contains(worker.rank);
                        assert !waitConn.containsKey(worker.rank);
                        shutdown.add(worker.rank);
                        channel.socket().close();
                        LOG.debug("Received " + worker.cmd + " signal from " + worker.rank);
                        continue;
                    }
                    assert START_CMD.equals(worker.cmd) || RECOVER_CMD.equals(worker.cmd);

                    if (null == linkMap) {
                        assert START_CMD.equals(worker.cmd);
                        linkMap = new LinkMap(tracker.workers);
                        for (int i = 0; i < tracker.workers; i++) {
                            todoNodes.add(i);
                        }
                    } else {
                        assert worker.worldSize == -1 || worker.worldSize == tracker.workers;
                    }

                    if (RECOVER_CMD.equals(worker.cmd)) {
                        assert worker.rank >= 0;
                    }

                    int rank = worker.decideRank(jobToRankMap);
                    if (-1 == rank) {
                        assert todoNodes.size() != 0;
                        pending.add(worker);
                        if (pending.size() == todoNodes.size()) {
                            Collections.sort(pending);
                            for (RabitWorker p : pending) {
                                rank = todoNodes.poll();
                                if (!NULL_STR.equals(p.jobId)) {
                                    jobToRankMap.put(p.jobId, rank);
                                }
                                p.assignRank(rank, waitConn, linkMap);

                                if (p.waitAccept > 0) {
                                    waitConn.put(rank, p);
                                }

                                LOG.debug("Received " + p.cmd +
                                        " signal from " + p.host + ":" + p.workerPort +
                                        ". Assigned rank " + p.rank
                                );
                            }
                        }
                        if (todoNodes.isEmpty()) {
                            LOG.debug("All " + tracker.workers + " Rabit workers are getting started.");
                        }
                    } else {
                        worker.assignRank(rank, waitConn, linkMap);
                        if (worker.waitAccept > 0) {
                            waitConn.put(rank, worker);
                        }
                    }
                } catch (IOException e) {
                    LOG.error("Exception in Rabit tracker.", e);
                }
            }
            LOG.debug("All Rabit nodes finished.");
        }
    }

    @Override
    public int waitFor(long timeout) {
        while(null != this.trackerThread && this.trackerThread.isAlive()) {
            try {
                this.trackerThread.join(timeout);
            } catch (InterruptedException e) {
                LOG.debug("Rabit tracker thread got suddenly interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }
        return 0;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        LOG.error("Uncaught exception occurred on Rabit tracker thread " + t.getName(), e);
        stop();
    }
}
