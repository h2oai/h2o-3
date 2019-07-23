package water;

public class H2ONodeTimestamp {

    /** In case we are using flatfile, we intern the nodes in the flatfile, but we do not know the timestamp of the remote
     node at that time. Therefore we set the timestamp to undefined and set it to correct value once we hear from the
     remote node
     */
    static final short UNDEFINED = 0;

    
    /** 
     * Check whether this timestamp is valid timestamp of running H2O node
     */
    public static boolean isDefined(short timestamp) {
        return timestamp != UNDEFINED;
    }
    
    /**
     * Select last 15 bytes from the jvm boot start time and return it as short. If the timestamp is 0, we increment it by
     * 1 to be able to distinguish between client and node as -0 is the same as 0.
     */
    private static short truncateTimestamp(long jvmStartTime){
        int bitMask = (1 << 15) - 1;
        // select the lower 15 bits
        short timestamp = (short) (jvmStartTime & bitMask);
        // if the timestamp is 0=(TIMESTAMP_UNDEFINED) return 1 to be able to distinguish between positive and negative values
        return timestamp == 0 ? 1 : timestamp;
    }


    /**
     * Calculate node timestamp from Current's node information. We use start of jvm boot time and information whether
     * we are client or not. We combine these 2 information and create a char(2 bytes) with this info in a single variable.
     */
    static short calculateNodeTimestamp() {
        return calculateNodeTimestamp(TimeLine.JVM_BOOT_MSEC, H2O.ARGS.client);
    }

    /**
     * Calculate node timestamp from the provided information. We use start of jvm boot time and information whether
     * we are client or not.
     *
     * The negative timestamp represents a client node, the positive one a regular H2O node
     *
     * @param bootTimestamp H2O node boot timestamp
     * @param amIClient true if this node is client, otherwise false
     */
    static short calculateNodeTimestamp(long bootTimestamp, boolean amIClient) {
        short timestamp = truncateTimestamp(bootTimestamp);
        //if we are client, return negative timestamp, otherwise positive
        return amIClient ? (short) -timestamp : timestamp;
    }

    /**
     * Decodes whether the node is client or regular node from the timestamp
     * @param timestamp timestamp
     * @return true if timestamp is from client node, false otherwise
     */
    static boolean decodeIsClient(short timestamp) {
        return timestamp < 0;
    }


    /**
     * This method checks whether the H2O node respawned based on its previous and current timestamp
     */
    static boolean hasNodeRespawned(short oldTimestamp, short newTimestamp) {
        return isDefined(oldTimestamp) && isDefined(newTimestamp) && oldTimestamp != newTimestamp;
    }
}
