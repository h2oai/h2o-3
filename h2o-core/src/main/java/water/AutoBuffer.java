package water;

import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Random;

import water.network.SocketChannelUtils;
import water.util.Log;
import water.util.StringUtils;
import water.util.TwoDimTable;

import static water.H2O.OptArgs.SYSTEM_PROP_PREFIX;

/** A ByteBuffer backed mixed Input/Output streaming class, using Iced serialization.
 *
 *  Reads/writes empty/fill the ByteBuffer as needed.  When it is empty/full it
 *  we go to the ByteChannel for more/less.  Because DirectByteBuffers are
 *  expensive to make, we keep a few pooled.
 *
 *  When talking to a remote H2O node, switches between UDP and TCP transport
 *  protocols depending on the message size.  The TypeMap is not included, and
 *  is assumed to exist on the remote H2O node.
 *
 *  Supports direct NIO FileChannel read/write to disk, used during user-mode
 *  swapping.  The TypeMap is not included on write, and is assumed to be the
 *  current map on read.
 *
 *  Support read/write from byte[] - and this defeats the purpose of a
 *  Streaming protocol, but is frequently handy for small structures.  The
 *  TypeMap is not included, and is assumed to be the current map on read.
 *
 *  Supports read/write from a standard Stream, which by default assumes it is
 *  NOT going in and out of the same Cloud, so the TypeMap IS included.  The
 *  serialized object can only be read back into the same minor version of H2O.
 *
 *  @author <a href="mailto:cliffc@h2o.ai"></a>
 */
public final class AutoBuffer {

  // Maximum size of an array we allow to allocate (the value is designed
  // to mimic the behavior of OpenJDK libraries)
  private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

  private static String H2O_SYSTEM_SERIALIZATION_IGNORE_VERSION = SYSTEM_PROP_PREFIX + "serialization.ignore.version";

  // The direct ByteBuffer for schlorping data about.
  // Set to null to indicate the AutoBuffer is closed.
  ByteBuffer _bb;
  public String sourceName = "???";

  public boolean isClosed() { return _bb == null ; }

  // The ByteChannel for moving data in or out.  Could be a SocketChannel (for
  // a TCP connection) or a FileChannel (spill-to-disk) or a DatagramChannel
  // (for a UDP connection).  Null on closed AutoBuffers.  Null on initial
  // remote-writing AutoBuffers which are still deciding UDP vs TCP.  Not-null
  // for open AutoBuffers doing file i/o or reading any TCP/UDP or having
  // written at least one buffer to TCP/UDP.
  private Channel _chan;

  // A Stream for moving data in.  Null unless this AutoBuffer is
  // stream-based, in which case _chan field is null.  This path supports
  // persistance: reading and writing objects from different H2O cluster
  // instances (but exactly the same H2O revision).  The only required
  // similarity is same-classes-same-fields; changes here will probably
  // silently crash.  If the fields are named the same but the semantics
  // differ, then again the behavior is probably silent crash.
  private  InputStream _is;
  private short[] _typeMap; // Mapping from input stream map to current map, or null

  // If we need a SocketChannel, raise the priority so we get the I/O over
  // with.  Do not want to have some TCP socket open, blocking the TCP channel
  // and then have the thread stalled out.  If we raise the priority - be sure
  // to lower it again.  Note this is for TCP channels ONLY, and only because
  // we are blocking another Node with I/O.
  private int _oldPrior = -1;

  // Where to send or receive data via TCP or UDP (choice made as we discover
  // how big the message is); used to lazily create a Channel.  If NULL, then
  // _chan should be a pre-existing Channel, such as a FileChannel.
  final H2ONode _h2o;

  // TRUE for read-mode.  FALSE for write-mode.  Can be flipped for rapid turnaround.
  private boolean _read;

  // TRUE if this AutoBuffer has never advanced past the first "page" of data.
  // The UDP-flavor, port# and task fields are only valid until we read over
  // them when flipping the ByteBuffer to the next chunk of data.  Used in
  // asserts all over the place.
  private boolean _firstPage;


  // Total size written out from 'new' to 'close'.  Only updated when actually
  // reading or writing data, or after close().  For profiling only.
  int _size;
  //int _zeros, _arys;
  // More profiling: start->close msec, plus nano's spent in blocking I/O
  // calls.  The difference between (close-start) and i/o msec is the time the
  // i/o thread spends doing other stuff (e.g. allocating Java objects or
  // (de)serializing).
  long _time_start_ms, _time_close_ms, _time_io_ns;
  // I/O persistence flavor: Value.ICE, NFS, HDFS, S3, TCP.  Used to record I/O time.
  final byte _persist;

  // The assumed max UDP packetsize
  static final int MTU = 1500-8/*UDP packet header size*/;

  // Enable this to test random TCP fails on open or write
  static final Random RANDOM_TCP_DROP = null; //new Random();

  static final java.nio.charset.Charset UTF_8 = java.nio.charset.Charset.forName("UTF-8");

  /** Incoming TCP request.  Make a read-mode AutoBuffer from the open Channel,
   *  figure the originating H2ONode from the first few bytes read.
   *
   *  remoteAddress set to null means that the communication is originating from non-h2o node, non-null value
   *  represents the case where the communication is coming from h2o node.
   *  */
  public AutoBuffer( ByteChannel sock, InetAddress remoteAddress  ) throws IOException {
    _chan = sock;
    raisePriority();            // Make TCP priority high
    _bb = BBP_BIG.make();       // Get a big / TPC-sized ByteBuffer
    _bb.flip();
    _read = true;               // Reading by default
    _firstPage = true;
    // Read Inet from socket, port from the stream, figure out H2ONode
    if(remoteAddress!=null) {
      _h2o = H2ONode.intern(remoteAddress, getPort());
    }else{
      // In case the communication originates from non-h2o node, we set _h2o node to null.
      // It is done for 2 reasons:
      //  - H2ONode.intern creates a new thread and if there's a lot of connections
      //    from non-h2o environment, it could end up with too many open files exception.
      //  - H2OIntern also reads port (getPort()) and additional information which we do not send
      //    in communication originating from non-h2o nodes
      _h2o = null;
    }
    _firstPage = true;          // Yes, must reset this.
    _time_start_ms = System.currentTimeMillis();
    _persist = Value.TCP;
  }

  /** Make an AutoBuffer to write to an H2ONode.  Requests for full buffer will
   *  open a TCP socket and roll through writing to the target.  Smaller
   *  requests will send via UDP.  Small requests get ordered by priority, so 
   *  that e.g. NACK and ACKACK messages have priority over most anything else.
   *  This helps in UDP floods to shut down flooding senders. */
  private byte _msg_priority; 
  AutoBuffer( H2ONode h2o, byte priority ) {
    // If UDP goes via TCP, we write into a HBB up front, because this will be copied again
    // into a large outgoing buffer.
    _bb = ByteBuffer.wrap(new byte[16]).order(ByteOrder.nativeOrder());
    _chan = null;               // Channel made lazily only if we write alot
    _h2o = h2o;
    _read = false;              // Writing by default
    _firstPage = true;          // Filling first page
    assert _h2o != null;
    _time_start_ms = System.currentTimeMillis();
    _persist = Value.TCP;
    _msg_priority = priority;
  }

  /** Spill-to/from-disk request. */
  public AutoBuffer( FileChannel fc, boolean read, byte persist ) {
    _bb = BBP_BIG.make();       // Get a big / TPC-sized ByteBuffer
    _chan = fc;                 // Write to read/write
    _h2o = null;                // File Channels never have an _h2o
    _read = read;               // Mostly assert reading vs writing
    if( read ) _bb.flip();
    _time_start_ms = System.currentTimeMillis();
    _persist = persist;         // One of Value.ICE, NFS, S3, HDFS
  }

  /** Read from UDP multicast.  Same as the byte[]-read variant, except there is an H2O. */
  AutoBuffer( DatagramPacket pack ) {
    _size = pack.getLength();
    _bb = ByteBuffer.wrap(pack.getData(), 0, pack.getLength()).order(ByteOrder.nativeOrder());
    _bb.position(0);
    _read = true;
    _firstPage = true;
    _chan = null;
    _h2o = H2ONode.intern(pack.getAddress(), getPort());
    _persist = 0;               // No persistance
  }

  /** Read from a UDP_TCP buffer; could be in the middle of a large buffer */
  AutoBuffer( H2ONode h2o, byte[] buf, int off, int len ) {
    assert buf != null : "null fed to ByteBuffer.wrap";
    _h2o = h2o;
    _bb = ByteBuffer.wrap(buf,off,len).order(ByteOrder.nativeOrder());
    _chan = null;
    _read = true;
    _firstPage = true;
    _persist = 0;               // No persistance
    _size = len;
  }

  /** Read from a fixed byte[]; should not be closed. */
  public AutoBuffer( byte[] buf ) { this(null,buf,0, buf.length); }

  /** Write to an ever-expanding byte[].  Instead of calling {@link #close()},
   *  call {@link #buf()} to retrieve the final byte[].  */
  public AutoBuffer( ) {
    _bb = ByteBuffer.wrap(new byte[16]).order(ByteOrder.nativeOrder());
    _chan = null;
    _h2o = null;
    _read = false;
    _firstPage = true;
    _persist = 0;               // No persistance
  }

  /** Write to a known sized byte[].  Instead of calling close(), call
   * {@link #bufClose()} to retrieve the final byte[]. */
  public AutoBuffer( int len ) {
    _bb = ByteBuffer.wrap(MemoryManager.malloc1(len)).order(ByteOrder.nativeOrder());
    _chan = null;
    _h2o = null;
    _read = false;
    _firstPage = true;
    _persist = 0;               // No persistance
  }

  /** Write to a persistent Stream, including all TypeMap info to allow later
   *  reloading (by the same exact rev of H2O). */
  public AutoBuffer( OutputStream os, boolean persist ) {
    _bb = ByteBuffer.wrap(MemoryManager.malloc1(BBP_BIG._size)).order(ByteOrder.nativeOrder());
    _read = false;
    _chan = Channels.newChannel(os);
    _h2o = null;
    _firstPage = true;
    _persist = 0;

    if( persist ) {
      String[] typeMap = (H2O.CLOUD.leader() == H2O.SELF) ?
              TypeMap.CLAZZES : FetchClazzes.fetchClazzes();
      put1(0x1C).put1(0xED).putStr(H2O.ABV.projectVersion()).putAStr(typeMap);
    }
    else put1(0);
  }

  /** Read from a persistent Stream (including all TypeMap info) into same
   *  exact rev of H2O). */
  public AutoBuffer( InputStream is ) {
    _chan = null;
    _h2o = null;
    _firstPage = true;
    _persist = 0;

    _read = true;
    _bb = ByteBuffer.wrap(MemoryManager.malloc1(BBP_BIG._size)).order(ByteOrder.nativeOrder());
    _bb.flip();
    _is = is;
    int b = get1U();
    if( b==0 ) return;          // No persistence info
    int magic = get1U();
    if( b!=0x1C || magic != 0xED ) throw new IllegalArgumentException("Missing magic number 0x1CED at stream start");
    checkVersion(getStr());
    String[] typeMap = getAStr();
    _typeMap = new short[typeMap.length];
    for( int i=0; i<typeMap.length; i++ )
      _typeMap[i] = (short)(typeMap[i]==null ? 0 : TypeMap.onIce(typeMap[i]));
  }

  private void checkVersion(String version) {
    final boolean ignoreVersion = Boolean.getBoolean(H2O_SYSTEM_SERIALIZATION_IGNORE_VERSION);
    if (! version.equals(H2O.ABV.projectVersion())) {
      String msg = "Found version "+version+", but running version "+H2O.ABV.projectVersion();
      if (ignoreVersion)
        Log.warn("Loading data from a different version! " + msg);
      else
        throw new IllegalArgumentException(msg);
    }
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[AB ").append(_read ? "read " : "write ");
    sb.append(_firstPage?"first ":"2nd ").append(_h2o);
    sb.append(" ").append(Value.nameOfPersist(_persist));
    if( _bb != null ) sb.append(" 0 <= ").append(_bb.position()).append(" <= ").append(_bb.limit());
    if( _bb != null ) sb.append(" <= ").append(_bb.capacity());
    return sb.append("]").toString();
  }

  // Fetch a DBB from an object pool... they are fairly expensive to make
  // because a native call is required to get the backing memory.  I've
  // included BB count tracking code to help track leaks.  As of 12/17/2012 the
  // leaks are under control, but figure this may happen again so keeping these
  // counters around.
  //
  // We use 2 pool sizes: lots of small UDP packet-sized buffers and fewer
  // larger TCP-sized buffers.
  private static final boolean DEBUG = Boolean.getBoolean("h2o.find-ByteBuffer-leaks");
  private static long HWM=0;

  static class BBPool {
    long _made, _cached, _freed;
    long _numer, _denom, _goal=4*H2O.NUMCPUS, _lastGoal;
    final ArrayList<ByteBuffer> _bbs = new ArrayList<>();
    final int _size;            // Big or small size of ByteBuffers

    BBPool( int sz) { _size=sz; }
    private ByteBuffer stats( ByteBuffer bb ) {
      if( !DEBUG ) return bb;
      if( ((_made+_cached)&255)!=255 ) return bb; // Filter printing to 1 in 256
      long now = System.currentTimeMillis();
      if( now < HWM ) return bb;
      HWM = now+1000;
      water.util.SB sb = new water.util.SB();
      sb.p("BB").p(this==BBP_BIG?1:0).p(" made=").p(_made).p(" -freed=").p(_freed).p(", cache hit=").p(_cached).p(" ratio=").p(_numer/_denom).p(", goal=").p(_goal).p(" cache size=").p(_bbs.size()).nl();
      for( int i=0; i<H2O.MAX_PRIORITY; i++ ) {
        int x = H2O.getWrkQueueSize(i);
        if( x > 0 ) sb.p('Q').p(i).p('=').p(x).p(' ');
      }
      Log.warn(sb.nl().toString());
      return bb;
    }

    ByteBuffer make() {
      while( true ) {             // Repeat loop for DBB OutOfMemory errors
        ByteBuffer bb=null;
        synchronized(_bbs) { 
          int sz = _bbs.size();
          if( sz > 0 ) { bb = _bbs.remove(sz-1); _cached++; _numer++; }
        }
        if( bb != null ) return stats(bb);
        // Cache empty; go get one from C/Native memory
        try {
          bb = ByteBuffer.allocateDirect(_size).order(ByteOrder.nativeOrder());
          synchronized(this) { _made++; _denom++; _goal = Math.max(_goal,_made-_freed); _lastGoal=System.nanoTime(); } // Goal was too low, raise it
          return stats(bb);
        } catch( OutOfMemoryError oome ) {
          // java.lang.OutOfMemoryError: Direct buffer memory
          if( !"Direct buffer memory".equals(oome.getMessage()) ) throw oome;
          System.out.println("OOM DBB - Sleeping & retrying");
          try { Thread.sleep(100); } catch( InterruptedException ignore ) { }
        }
      }
    }
    void free(ByteBuffer bb) {
      // Heuristic: keep the ratio of BB's made to cache-hits at a fixed level.
      // Free to GC if ratio is high, free to internal cache if low.
      long ratio = _numer/(_denom+1);
      synchronized(_bbs) { 
        if( ratio < 100 || _bbs.size() < _goal ) { // low hit/miss ratio or below goal
          bb.clear();           // Clear-before-add
          _bbs.add(bb);
        } else _freed++;        // Toss the extras (above goal & ratio)

        long now = System.nanoTime();
        if( now-_lastGoal > 1000000000L ) { // Once/sec, drop goal by 10%
          _lastGoal = now;
          if( ratio > 110 )     // If ratio is really high, lower goal
            _goal=Math.max(4*H2O.NUMCPUS,(long)(_goal*0.99));
          // Once/sec, lower numer/denom... means more recent activity outweighs really old stuff
          long denom = (long) (0.99 * _denom); // Proposed reduction
          if( denom > 10 ) {                   // Keep a little precision
            _numer = (long) (0.99 * _numer);   // Keep ratio between made & cached the same
            _denom = denom;                    // ... by lowering both by 10%
          }
        }
      }
    }
    static int FREE( ByteBuffer bb ) {
      if(bb.isDirect())
        (bb.capacity()==BBP_BIG._size ? BBP_BIG : BBP_SML).free(bb);
      return 0;                 // Flow coding
    }
  }
  static BBPool BBP_SML = new BBPool( 2*1024); // Bytebuffer "common small size", for UDP
  static BBPool BBP_BIG = new BBPool(64*1024); // Bytebuffer "common  big  size", for TCP
  public static int TCP_BUF_SIZ = BBP_BIG._size;

  private int bbFree() {
    if(_bb != null && _bb.isDirect())
      BBPool.FREE(_bb);
    _bb = null;
    return 0;                   // Flow-coding
  }

  // You thought TCP was a reliable protocol, right?  WRONG!  Fails 100% of the
  // time under heavy network load.  Connection-reset-by-peer & connection
  // timeouts abound, even after a socket open and after a 1st successful
  // ByteBuffer write.  It *appears* that the reader is unaware that a writer
  // was told "go ahead and write" by the TCP stack, so all these fails are
  // only on the writer-side.
  public static class AutoBufferException extends RuntimeException {
    public final IOException _ioe;
    AutoBufferException( IOException ioe ) { _ioe = ioe; }
  }

  // For reads, just assert all was read and close and release resources.
  // (release ByteBuffer back to the common pool).  For writes, force any final
  // bytes out.  If the write is to an H2ONode and is short, send via UDP.
  // AutoBuffer close calls order; i.e. a reader close() will block until the
  // writer does a close().
  public final int close() {
    //if( _size > 2048 ) System.out.println("Z="+_zeros+" / "+_size+", A="+_arys);
    if( isClosed() ) return 0;            // Already closed
    assert _h2o != null || _chan != null || _is != null; // Byte-array backed should not be closed

    try {
      if( _chan == null ) {     // No channel?
        if( _read ) {
          if( _is != null ) _is.close();
          return 0;
        } else {                // Write
          // For small-packet write, send via UDP.  Since nothing is sent until
          // now, this close() call trivially orders - since the reader will not
          // even start (much less close()) until this packet is sent.
          if( _bb.position() < MTU) return udpSend();
          // oops - Big Write, switch to TCP and finish out there
        }
      }
      // Force AutoBuffer 'close' calls to order; i.e. block readers until
      // writers do a 'close' - by writing 1 more byte in the close-call which
      // the reader will have to wait for.
      if( hasTCP()) {          // TCP connection?
        try {
          if( _read ) {         // Reader?
            int x = get1U();    // Read 1 more byte
            assert x == 0xab : "AB.close instead of 0xab sentinel got "+x+", "+this;
            assert _chan != null; // chan set by incoming reader, since we KNOW it is a TCP
            // Write the reader-handshake-byte.
            SocketChannelUtils.underlyingSocketChannel(_chan).socket().getOutputStream().write(0xcd);
            // do not close actually reader socket; recycle it in TCPReader thread
          } else {              // Writer?
            put1(0xab);         // Write one-more byte  ; might set _chan from null to not-null
            sendPartial();      // Finish partial writes; might set _chan from null to not-null
            assert _chan != null; // _chan is set not-null now!
            // Read the writer-handshake-byte.
            int x = SocketChannelUtils.underlyingSocketChannel(_chan).socket().getInputStream().read();
            // either TCP con was dropped or other side closed connection without reading/confirming (e.g. task was cancelled).
            if( x == -1 ) throw new IOException("Other side closed connection before handshake byte read");
            assert x == 0xcd : "Handshake; writer expected a 0xcd from reader but got "+x;
          }
        } catch( IOException ioe ) {
          try { _chan.close(); } catch( IOException ignore ) {} // Silently close
          _chan = null;         // No channel now, since i/o error
          throw ioe;            // Rethrow after close
        } finally {
          if( !_read ) _h2o.freeTCPSocket((ByteChannel) _chan); // Recycle writable TCP channel
          restorePriority();        // And if we raised priority, lower it back
        }

      } else {                      // FileChannel
        if( !_read ) sendPartial(); // Finish partial file-system writes
        _chan.close();
        _chan = null;           // Closed file channel
      }

    } catch( IOException e ) {  // Dunno how to handle so crash-n-burn
      throw new AutoBufferException(e);
    } finally {
      bbFree();
      _time_close_ms = System.currentTimeMillis();
//      TimeLine.record_IOclose(this,_persist); // Profile AutoBuffer connections
      assert isClosed();
    }
    return 0;
  }

  // Need a sock for a big read or write operation.
  // See if we got one already, else open a new socket.
  private void tcpOpen() throws IOException {
    assert _firstPage && _bb.limit() >= 1+2+4; // At least something written
    assert _chan == null;
//    assert _bb.position()==0;
    _chan = _h2o.getTCPSocket();
    raisePriority();
  }

  // Just close the channel here without reading anything.  Without the task
  // object at hand we do not know what (how many bytes) should we read from
  // the channel.  And since the other side will try to read confirmation from
  // us before closing the channel, we can not read till the end.  So we just
  // close the channel and let the other side to deal with it and figure out
  // the task has been cancelled (still sending ack ack back).
  void drainClose() {
    if( isClosed() ) return;              // Already closed
    final Channel chan = _chan;       // Read before closing
    assert _h2o != null || chan != null;  // Byte-array backed should not be closed
    if( chan != null ) {                  // Channel assumed sick from prior IOException
      try { chan.close(); } catch( IOException ignore ) {} // Silently close
      _chan = null;                       // No channel now!
      if( !_read && SocketChannelUtils.isSocketChannel(chan)) _h2o.freeTCPSocket((ByteChannel) chan); // Recycle writable TCP channel
    }
    restorePriority();          // And if we raised priority, lower it back
    bbFree();
    _time_close_ms = System.currentTimeMillis();
//    TimeLine.record_IOclose(this,_persist); // Profile AutoBuffer connections
    assert isClosed();
  }

  // True if we opened a TCP channel, or will open one to close-and-send
  boolean hasTCP() { assert !isClosed(); return SocketChannelUtils.isSocketChannel(_chan) || (_h2o!=null && _bb.position() >= MTU); }

  // Size in bytes sent, after a close()
  int size() { return _size; }
  //int zeros() { return _zeros; }

  public int position () { return _bb.position(); }

  public AutoBuffer position(int p) {_bb.position(p); return this;}
  /** Skip over some bytes in the byte buffer.  Caller is responsible for not
   *  reading off end of the bytebuffer; generally this is easy for
   *  array-backed autobuffers and difficult for i/o-backed bytebuffers. */
  public void skip(int skip) { _bb.position(_bb.position()+skip); }

  // Return byte[] from a writable AutoBuffer
  public final byte[] buf() {
    assert _h2o==null && _chan==null && !_read && !_bb.isDirect();
    return MemoryManager.arrayCopyOfRange(_bb.array(), _bb.arrayOffset(), _bb.position());
  }


  public final byte[] bufClose() {
    byte[] res = _bb.array();
    bbFree();
    return res;
  }
  // For TCP sockets ONLY, raise the thread priority.  We assume we are
  // blocking other Nodes with our network I/O, so try to get the I/O
  // over with.
  private void raisePriority() {
    if(_oldPrior == -1){
      assert SocketChannelUtils.isSocketChannel(_chan);
      _oldPrior = Thread.currentThread().getPriority();
      Thread.currentThread().setPriority(Thread.MAX_PRIORITY-1);
    }
  }

  private void restorePriority() {
    if( _oldPrior == -1 ) return;
    Thread.currentThread().setPriority(_oldPrior);
    _oldPrior = -1;
  }

  // Send via UDP socket.  Unlike eg TCP sockets, we only need one for sending
  // so we keep a global one.  Also, we do not close it when done, and we do
  // not connect it up-front to a target - but send the entire packet right now.
  private int udpSend() throws IOException {
    assert _chan == null;
    TimeLine.record_send(this,false);
    _size = _bb.position();
    assert _size < AutoBuffer.BBP_SML._size;
    _bb.flip();                 // Flip for sending
    if( _h2o==H2O.SELF ) {      // SELF-send is the multi-cast signal
      water.init.NetworkInit.multicast(_bb, _msg_priority);
    } else {                    // Else single-cast send
      // Send via bulk TCP
      _h2o.sendMessage(_bb, _msg_priority);
    }
    return 0;                   // Flow-coding
  }

  // Flip to write-mode
  AutoBuffer clearForWriting(byte priority) {
    assert _read;
    _read = false;
    _msg_priority = priority;
    _bb.clear();
    _firstPage = true;
    return this;
  }
  // Flip to read-mode
  public AutoBuffer flipForReading() {
    assert !_read;
    _read = true;
    _bb.flip();
    _firstPage = true;
    return this;
  }


  /** Ensure the buffer has space for sz more bytes */
  private ByteBuffer getSp( int sz ) { return sz > _bb.remaining() ? getImpl(sz) : _bb; }

  /** Ensure buffer has at least sz bytes in it.
   * - Also, set position just past this limit for future reading. */
  private ByteBuffer getSz(int sz) {
    assert _firstPage : "getSz() is only valid for early UDP bytes";
    if( sz > _bb.limit() ) getImpl(sz);
    _bb.position(sz);
    return _bb;
  }

  private ByteBuffer getImpl( int sz ) {
    assert _read : "Reading from a buffer in write mode";
    _bb.compact();            // Move remaining unread bytes to start of buffer; prep for reading
    // Its got to fit or we asked for too much
    assert _bb.position()+sz <= _bb.capacity() : "("+_bb.position()+"+"+sz+" <= "+_bb.capacity()+")";
    long ns = System.nanoTime();
    while( _bb.position() < sz ) { // Read until we got enuf
      try {
        int res = readAnInt(); // Read more
        // Readers are supposed to be strongly typed and read the exact expected bytes.
        // However, if a TCP connection fails mid-read we'll get a short-read.
        // This is indistinguishable from a mis-alignment between the writer and reader!
        if( res <= 0 )
          throw new AutoBufferException(new EOFException("Reading "+sz+" bytes, AB="+this));
        if( _is != null ) _bb.position(_bb.position()+res); // Advance BB for Streams manually
        _size += res;            // What we read
      } catch( IOException e ) { // Dunno how to handle so crash-n-burn
        // Linux/Ubuntu message for a reset-channel
        if( e.getMessage().equals("An existing connection was forcibly closed by the remote host") )
          throw new AutoBufferException(e);
        // Windows message for a reset-channel
        if( e.getMessage().equals("An established connection was aborted by the software in your host machine") )
          throw new AutoBufferException(e);
        throw Log.throwErr(e);
      }
    }
    _time_io_ns += (System.nanoTime()-ns);
    _bb.flip();                 // Prep for handing out bytes
    //for( int i=0; i < _bb.limit(); i++ ) if( _bb.get(i)==0 ) _zeros++;
    _firstPage = false;         // First page of data is gone gone gone
    return _bb;
  }

  private int readAnInt() throws IOException {
    if (_is == null) return ((ReadableByteChannel) _chan).read(_bb);

    final byte[] array = _bb.array();
    final int position = _bb.position();
    final int remaining = _bb.remaining();
    try {
      return _is.read(array, position, remaining);
    } catch (IOException ioe) {
      throw new IOException("Failed reading " + remaining + " bytes into buffer[" + array.length + "] at " + position + " from " + sourceName + " " + _is, ioe);
    }
  }

  /** Put as needed to keep from overflowing the ByteBuffer. */
  private ByteBuffer putSp( int sz ) {
    assert !_read;
    if (sz > _bb.remaining()) {
      if ((_h2o == null && _chan == null) || (_bb.hasArray() && _bb.capacity() < BBP_BIG._size))
        expandByteBuffer(sz);
      else
        sendPartial();
      assert sz <= _bb.remaining();
    }
    return _bb;
  }

  // Do something with partial results, because the ByteBuffer is full.
  // If we are doing I/O, ship the bytes we have now and flip the ByteBuffer.
  private ByteBuffer sendPartial() {
    // Doing I/O with the full ByteBuffer - ship partial results
    _size += _bb.position();
    if( _chan == null )
      TimeLine.record_send(this, true);

    _bb.flip(); // Prep for writing.
    try {
      if( _chan == null )
        tcpOpen(); // This is a big operation.  Open a TCP socket as-needed.
      //for( int i=0; i < _bb.limit(); i++ ) if( _bb.get(i)==0 ) _zeros++;
      long ns = System.nanoTime();
      while( _bb.hasRemaining() ) {
        ((WritableByteChannel) _chan).write(_bb);
        if( RANDOM_TCP_DROP != null && SocketChannelUtils.isSocketChannel(_chan) && RANDOM_TCP_DROP.nextInt(100) == 0 )
          throw new IOException("Random TCP Write Fail");
      }
      _time_io_ns += (System.nanoTime()-ns);
    } catch( IOException e ) {  // Some kind of TCP fail?
      // Change to an unchecked exception (so we don't have to annotate every
      // frick'n put1/put2/put4/read/write call).  Retry & recovery happens at
      // a higher level.  AutoBuffers are used for many things including e.g.
      // disk i/o & UDP writes; this exception only happens on a failed TCP
      // write - and we don't want to make the other AutoBuffer users have to
      // declare (and then ignore) this exception.
      throw new AutoBufferException(e);
    }
    _firstPage = false;
    _bb.clear();
    return _bb;
  }

  // Called when the byte buffer doesn't have enough room
  // If buffer is array backed, and the needed room is small,
  // increase the size of the backing array,
  // otherwise dump into a large direct buffer
  private ByteBuffer expandByteBuffer(int sizeHint) {
    final long needed = (long) sizeHint - _bb.remaining() + _bb.capacity(); // Max needed is 2G
    if ((_h2o==null && _chan == null) || (_bb.hasArray() && needed < MTU)) {
      if (needed > MAX_ARRAY_SIZE) {
        throw new IllegalArgumentException("Cannot allocate more than 2GB array: sizeHint="+sizeHint+", "
                + "needed="+needed
                + ", bb.remaining()=" + _bb.remaining() + ", bb.capacity()="+_bb.capacity());
      }
      byte[] ary = _bb.array();
      // just get twice what is currently needed but not more then max array size (2G)
      // Be careful not to overflow because of integer math!
      int newLen = (int) Math.min(1L << (water.util.MathUtils.log2(needed)+1), MAX_ARRAY_SIZE);
      int oldpos = _bb.position();
      _bb = ByteBuffer.wrap(MemoryManager.arrayCopyOfRange(ary,0,newLen),oldpos,newLen-oldpos)
          .order(ByteOrder.nativeOrder());
    } else if (_bb.capacity() != BBP_BIG._size) { //avoid expanding existing BBP items
      int oldPos = _bb.position();
      _bb.flip();
      _bb = BBP_BIG.make().put(_bb);
      _bb.position(oldPos);
    }
    return _bb;
  }

  @SuppressWarnings("unused")  public String getStr(int off, int len) {
    return new String(_bb.array(), _bb.arrayOffset()+off, len, UTF_8);
  }

  // -----------------------------------------------
  // Utility functions to get various Java primitives
  @SuppressWarnings("unused")  public boolean getZ() { return get1()!=0; }
  @SuppressWarnings("unused")  public byte   get1 () { return getSp(1).get      (); }
  @SuppressWarnings("unused")  public int    get1U() { return get1() & 0xFF;        }
  @SuppressWarnings("unused")  public char   get2 () { return getSp(2).getChar  (); }
  @SuppressWarnings("unused")  public short   get2s () { return getSp(2).getShort  (); }

  @SuppressWarnings("unused")  public int    get3 () { getSp(3); return get1U() | get1U() << 8 | get1U() << 16; }
  @SuppressWarnings("unused")  public int    get4 () { return getSp(4).getInt   (); }
  @SuppressWarnings("unused")  public float  get4f() { return getSp(4).getFloat (); }
  @SuppressWarnings("unused")  public long   get8 () { return getSp(8).getLong  (); }
  @SuppressWarnings("unused")  public double get8d() { return getSp(8).getDouble(); }


  int    get1U(int off) { return _bb.get    (off)&0xFF; }
  int    get4 (int off) { return _bb.getInt (off); }
  long   get8 (int off) { return _bb.getLong(off); }

  @SuppressWarnings("unused")  public AutoBuffer putZ (boolean b){ return put1(b?1:0); }
  @SuppressWarnings("unused")  public AutoBuffer put1 (   int b) { assert b >= -128 && b <= 255 : ""+b+" is not a byte";
                                                            putSp(1).put((byte)b); return this; }
  @SuppressWarnings("unused")  public AutoBuffer put2 (  char c) { putSp(2).putChar  (c); return this; }
  @SuppressWarnings("unused")  public AutoBuffer put2 ( short s) { putSp(2).putShort (s); return this; }
  @SuppressWarnings("unused")  public AutoBuffer put2s ( short s) { return put2(s); }

  @SuppressWarnings("unused")  public AutoBuffer put3( int x ) {   assert (-1<<24) <= x && x < (1<<24);
                                                            return put1((x)&0xFF).put1((x >> 8)&0xFF).put1(x >> 16); }
  @SuppressWarnings("unused")  public AutoBuffer put4 (   int i) { putSp(4).putInt   (i); return this; }
  @SuppressWarnings("unused")  public AutoBuffer put4f( float f) { putSp(4).putFloat (f); return this; }
  @SuppressWarnings("unused")  public AutoBuffer put8 (  long l) { putSp(8).putLong  (l); return this; }
  @SuppressWarnings("unused")  public AutoBuffer put8d(double d) { putSp(8).putDouble(d); return this; }

  public AutoBuffer put(Freezable f) {
    if( f == null ) return putInt(TypeMap.NULL);
    assert f.frozenType() > 0 : "No TypeMap for "+f.getClass().getName();
    putInt(f.frozenType());
    return f.write(this);
  }

  public <T extends Freezable> T get() {
    int id = getInt();
    if( id == TypeMap.NULL ) return null;
    if( _is!=null ) id = _typeMap[id];
    return (T)TypeMap.newFreezable(id).read(this);
  }
  public <T extends Freezable> T get(Class<T> tc) {
    int id = getInt();
    if( id == TypeMap.NULL ) return null;
    if( _is!=null ) id = _typeMap[id];
    assert tc.isInstance(TypeMap.theFreezable(id)):tc.getName() + " != " + TypeMap.theFreezable(id).getClass().getName() + ", id = " + id;
    return (T)TypeMap.newFreezable(id).read(this);
  }

  // Write Key's target IFF the Key is not null; target can be null.
  public AutoBuffer putKey(Key k) {
    if( k==null ) return this;    // Key is null ==> write nothing
    Keyed kd = DKV.getGet(k);
    put(kd);
    return kd == null ? this : kd.writeAll_impl(this);
  }
  public Keyed getKey(Key k, Futures fs) { 
    return k==null ? null : getKey(fs); // Key is null ==> read nothing
  }
  public Keyed getKey(Futures fs) { 
    Keyed kd = get(Keyed.class);
    if( kd == null ) return null;
    DKV.put(kd,fs);
    return kd.readAll_impl(this,fs);
  }

  // Put a (compressed) integer.  Specifically values in the range -1 to ~250
  // will take 1 byte, values near a Short will take 1+2 bytes, values near an
  // Int will take 1+4 bytes, and bigger values 1+8 bytes.  This compression is
  // optimized for small integers (including -1 which is often used as a "array
  // is null" flag when passing the array length).
  public AutoBuffer putInt(int x) {
    if( 0 <= (x+1)&& (x+1) <= 253 ) return put1(x+1);
    if( Short.MIN_VALUE <= x && x <= Short.MAX_VALUE ) return put1(255).put2((short)x);
    return put1(254).put4(x);
  }
  // Get a (compressed) integer.  See above for the compression strategy and reasoning.
  int getInt( ) {
    int x = get1U();
    if( x <= 253 ) return x-1;
    if( x==255 ) return (short)get2();
    assert x==254;
    return get4();
  }

  // Put a zero-compressed array.  Compression is:
  //  If null : putInt(-1)
  //  Else
  //    putInt(# of leading nulls)
  //    putInt(# of non-nulls)
  //    If # of non-nulls is > 0, putInt( # of trailing nulls)
  long putZA( Object[] A ) {
    if( A==null ) { putInt(-1); return 0; }
    int x=0; for( ; x<A.length; x++ ) if( A[x  ]!=null ) break;
    int y=A.length; for( ; y>x; y-- ) if( A[y-1]!=null ) break;
    putInt(x);                  // Leading zeros to skip
    putInt(y-x);                // Mixed non-zero guts in middle
    if( y > x )                 // If any trailing nulls
      putInt(A.length-y);       // Trailing zeros
    return ((long)x<<32)|(y-x); // Return both leading zeros, and middle non-zeros
  }
  // Get the lengths of a zero-compressed array.
  // Returns -1 if null.
  // Returns a long of (leading zeros | middle non-zeros).
  // If there are non-zeros, caller has to read the trailing zero-length.
  long getZA( ) {
    int x=getInt();             // Length of leading zeros
    if( x == -1 ) return -1;    // or a null
    int nz=getInt();            // Non-zero in the middle
    return ((long)x<<32)|(long)nz; // Return both ints
  }

  // TODO: untested. . .
  @SuppressWarnings("unused")
  public AutoBuffer putAEnum(Enum[] enums) {
    //_arys++;
    long xy = putZA(enums);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putEnum(enums[i]);
    return this;
  }

  @SuppressWarnings("unused")
  public <E extends Enum> E[] getAEnum(E[] values) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    E[] ts = (E[]) Array.newInstance(values.getClass().getComponentType(), x+y+z);
    for( int i = x; i < x+y; ++i ) ts[i] = getEnum(values);
    return ts;
  }

  @SuppressWarnings("unused")
  public AutoBuffer putA(Freezable[] fs) {
    //_arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) put(fs[i]);
    return this;
  }
  public AutoBuffer putAA(Freezable[][] fs) {
    //_arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA(fs[i]);
    return this;
  }
  @SuppressWarnings("unused") public AutoBuffer putAAA(Freezable[][][] fs) {
    //_arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putAA(fs[i]);
    return this;
  }

  public <T extends Freezable> T[] getA(Class<T> tc) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    T[] ts = (T[]) Array.newInstance(tc, x+y+z);
    for( int i = x; i < x+y; ++i ) ts[i] = get(tc);
    return ts;
  }
  public <T extends Freezable> T[][] getAA(Class<T> tc) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    Class<T[]> tcA = (Class<T[]>) Array.newInstance(tc, 0).getClass();
    T[][] ts = (T[][]) Array.newInstance(tcA, x+y+z);
    for( int i = x; i < x+y; ++i ) ts[i] = getA(tc);
    return ts;
  }
  @SuppressWarnings("unused") public <T extends Freezable> T[][][] getAAA(Class<T> tc) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    Class<T[]  > tcA  = (Class<T[]  >) Array.newInstance(tc , 0).getClass();
    Class<T[][]> tcAA = (Class<T[][]>) Array.newInstance(tcA, 0).getClass();
    T[][][] ts = (T[][][]) Array.newInstance(tcAA, x+y+z);
    for( int i = x; i < x+y; ++i ) ts[i] = getAA(tc);
    return ts;
  }

  public AutoBuffer putAStr(String[] fs)    {
    //_arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putStr(fs[i]);
    return this;
  }
  public String[] getAStr() {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    String[] ts = new String[x+y+z];
    for( int i = x; i < x+y; ++i ) ts[i] = getStr();
    return ts;
  }

  @SuppressWarnings("unused")  public AutoBuffer putAAStr(String[][] fs)    {
    //_arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putAStr(fs[i]);
    return this;
  }
  @SuppressWarnings("unused")  public String[][] getAAStr() {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    String[][] ts = new String[x+y+z][];
    for( int i = x; i < x+y; ++i ) ts[i] = getAStr();
    return ts;
  }

  // Read the smaller of _bb.remaining() and len into buf.
  // Return bytes read, which could be zero.
  int read( byte[] buf, int off, int len ) {
    int sz = Math.min(_bb.remaining(),len);
    _bb.get(buf,off,sz);
    return sz;
  }

  // -----------------------------------------------
  // Utility functions to handle common UDP packet tasks.
  // Get the 1st control byte
  int  getCtrl( ) { return getSz(1).get(0)&0xFF; }
  // Get the port in next 2 bytes
  int  getPort( ) { return getSz(1+2).getChar(1); }
  // Get the task# in the next 4 bytes
  int  getTask( ) { return getSz(1+2+4).getInt(1+2); }
  // Get the flag in the next 1 byte
  int  getFlag( ) { return getSz(1+2+4+1).get(1+2+4); }

  /**
   * Write UDP into the ByteBuffer with custom sender's port number
   *
   * This method sets the ctrl, port, task.
   * Ready to write more bytes afterwards
   *
   * @param type type of the UDP datagram
   * @param senderPort port of the sender of the datagram
   */
  AutoBuffer putUdp(UDP.udp type, int senderPort){
    assert _bb.position() == 0;
    putSp(_bb.position()+1+2);
    _bb.put    ((byte)type.ordinal());
    _bb.putChar((char)senderPort    );
    return this;
  }

  /**
   * Write UDP into the ByteBuffer with the current node as the sender.
   *
   * This method sets the ctrl, port, task.
   * Ready to write more bytes afterwards
   *
   * @param type type of the UDP datagram
   */
  AutoBuffer putUdp (UDP.udp type) {
    return putUdp(type, H2O.H2O_PORT); // Outgoing port is always the sender's (me) port
  }

  AutoBuffer putTask(UDP.udp type, int tasknum) {
    return putUdp(type).put4(tasknum);
  }
  AutoBuffer putTask(int ctrl, int tasknum) {
    assert _bb.position() == 0;
    putSp(_bb.position()+1+2+4);
    _bb.put((byte)ctrl).putChar((char)H2O.H2O_PORT).putInt(tasknum);
    return this;
  }

  // -----------------------------------------------
  // Utility functions to read & write arrays

  public boolean[] getAZ() {
    int len = getInt();
    if (len == -1) return null;
    boolean[] r = new boolean[len];
    for (int i=0;i<len;++i) r[i] = getZ();
    return r;
  }

  public byte[] getA1( ) {
    //_arys++;
    int len = getInt();
    return len == -1 ? null : getA1(len);
  }
  public byte[] getA1( int len ) {
    byte[] buf = MemoryManager.malloc1(len);
    int sofar = 0;
    while( sofar < len ) {
      int more = Math.min(_bb.remaining(), len - sofar);
      _bb.get(buf, sofar, more);
      sofar += more;
      if( sofar < len ) getSp(Math.min(_bb.capacity(), len-sofar));
    }
    return buf;
  }

  public short[] getA2( ) {
    //_arys++;
    int len = getInt(); if( len == -1 ) return null;
    short[] buf = MemoryManager.malloc2(len);
    int sofar = 0;
    while( sofar < buf.length ) {
      ShortBuffer as = _bb.asShortBuffer();
      int more = Math.min(as.remaining(), len - sofar);
      as.get(buf, sofar, more);
      sofar += more;
      _bb.position(_bb.position() + as.position()*2);
      if( sofar < len ) getSp(Math.min(_bb.capacity()-1, (len-sofar)*2));
    }
    return buf;
  }

  public int[] getA4( ) {
    //_arys++;
    int len = getInt(); if( len == -1 ) return null;
    int[] buf = MemoryManager.malloc4(len);
    int sofar = 0;
    while( sofar < buf.length ) {
      IntBuffer as = _bb.asIntBuffer();
      int more = Math.min(as.remaining(), len - sofar);
      as.get(buf, sofar, more);
      sofar += more;
      _bb.position(_bb.position() + as.position()*4);
      if( sofar < len ) getSp(Math.min(_bb.capacity()-3, (len-sofar)*4));
    }
    return buf;
  }
  public float[] getA4f( ) {
    //_arys++;
    int len = getInt(); if( len == -1 ) return null;
    float[] buf = MemoryManager.malloc4f(len);
    int sofar = 0;
    while( sofar < buf.length ) {
      FloatBuffer as = _bb.asFloatBuffer();
      int more = Math.min(as.remaining(), len - sofar);
      as.get(buf, sofar, more);
      sofar += more;
      _bb.position(_bb.position() + as.position()*4);
      if( sofar < len ) getSp(Math.min(_bb.capacity()-3, (len-sofar)*4));
    }
    return buf;
  }
  public long[] getA8( ) {
    //_arys++;
    // Get the lengths of lead & trailing zero sections, and the non-zero
    // middle section.
    int x = getInt(); if( x == -1 ) return null;
    int y = getInt();           // Non-zero in the middle
    int z = y==0 ? 0 : getInt();// Trailing zeros
    long[] buf = MemoryManager.malloc8(x+y+z);
    switch( get1U() ) {      // 1,2,4 or 8 for how the middle section is passed
    case 1: for( int i=x; i<x+y; i++ ) buf[i] =       get1U(); return buf;
    case 2: for( int i=x; i<x+y; i++ ) buf[i] = (short)get2(); return buf;
    case 4: for( int i=x; i<x+y; i++ ) buf[i] =        get4(); return buf;
    case 8: break;
    default: throw H2O.fail();
    }

    int sofar = x;
    while( sofar < x+y ) {
      LongBuffer as = _bb.asLongBuffer();
      int more = Math.min(as.remaining(), x+y - sofar);
      as.get(buf, sofar, more);
      sofar += more;
      _bb.position(_bb.position() + as.position()*8);
      if( sofar < x+y ) getSp(Math.min(_bb.capacity()-7, (x+y-sofar)*8));
    }
    return buf;
  }
  public double[] getA8d( ) {
    //_arys++;
    int len = getInt(); if( len == -1 ) return null;
    double[] buf = MemoryManager.malloc8d(len);
    int sofar = 0;
    while( sofar < len ) {
      DoubleBuffer as = _bb.asDoubleBuffer();
      int more = Math.min(as.remaining(), len - sofar);
      as.get(buf, sofar, more);
      sofar += more;
      _bb.position(_bb.position() + as.position()*8);
      if( sofar < len ) getSp(Math.min(_bb.capacity()-7, (len-sofar)*8));
    }
    return buf;
  }
  @SuppressWarnings("unused")
  public byte[][] getAA1( ) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    byte[][] ary  = new byte[x+y+z][];
    for( int i=x; i<x+y; i++ ) ary[i] = getA1();
    return ary;
  }
  @SuppressWarnings("unused")
  public short[][] getAA2( ) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    short[][] ary  = new short[x+y+z][];
    for( int i=x; i<x+y; i++ ) ary[i] = getA2();
    return ary;
  }
  public int[][] getAA4( ) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    int[][] ary  = new int[x+y+z][];
    for( int i=x; i<x+y; i++ ) ary[i] = getA4();
    return ary;
  }
  @SuppressWarnings("unused")  public float[][] getAA4f( ) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    float[][] ary  = new float[x+y+z][];
    for( int i=x; i<x+y; i++ ) ary[i] = getA4f();
    return ary;
  }
  public long[][] getAA8( ) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    long[][] ary  = new long[x+y+z][];
    for( int i=x; i<x+y; i++ ) ary[i] = getA8();
    return ary;
  }
  @SuppressWarnings("unused")  public double[][] getAA8d( ) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    double[][] ary  = new double[x+y+z][];
    for( int i=x; i<x+y; i++ ) ary[i] = getA8d();
    return ary;
  }
  @SuppressWarnings("unused")  public int[][][] getAAA4( ) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    int[][][] ary  = new int[x+y+z][][];
    for( int i=x; i<x+y; i++ ) ary[i] = getAA4();
    return ary;
  }
  @SuppressWarnings("unused")  public long[][][] getAAA8( ) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    long[][][] ary  = new long[x+y+z][][];
    for( int i=x; i<x+y; i++ ) ary[i] = getAA8();
    return ary;
  }
  public double[][][] getAAA8d( ) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    double[][][] ary  = new double[x+y+z][][];
    for( int i=x; i<x+y; i++ ) ary[i] = getAA8d();
    return ary;
  }

  public String getStr( ) {
    int len = getInt();
    return len == -1 ? null : new String(getA1(len), UTF_8);
  }

  public <E extends Enum> E getEnum(E[] values ) {
    int idx = get1();
    return idx == -1 ? null : values[idx];
  }

  public AutoBuffer putAZ( boolean[] ary ) {
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
    for (boolean anAry : ary) putZ(anAry);
    return this;
  }

  public AutoBuffer putA1( byte[] ary ) {
    //_arys++;
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
    return putA1(ary,ary.length);
  }
  public AutoBuffer putA1( byte[] ary, int length ) { return putA1(ary,0,length); }
  public AutoBuffer putA1( byte[] ary, int sofar, int length ) {
    if (length - sofar > _bb.remaining()) expandByteBuffer(length-sofar);
    while( sofar < length ) {
      int len = Math.min(length - sofar, _bb.remaining());
      _bb.put(ary, sofar, len);
      sofar += len;
      if( sofar < length ) sendPartial();
    }
    return this;
  }
  AutoBuffer putA2( short[] ary ) {
    //_arys++;
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
    if (ary.length*2 > _bb.remaining()) expandByteBuffer(ary.length*2);
    int sofar = 0;
    while( sofar < ary.length ) {
      ShortBuffer sb = _bb.asShortBuffer();
      int len = Math.min(ary.length - sofar, sb.remaining());
      sb.put(ary, sofar, len);
      sofar += len;
      _bb.position(_bb.position() + sb.position()*2);
      if( sofar < ary.length ) sendPartial();
    }
    return this;
  }
  public AutoBuffer putA4( int[] ary ) {
    //_arys++;
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
    // Note: based on Brandon commit this should improve performance during parse (7d950d622ee3037555ecbab0e39404f8f0917652)
    if (ary.length*4 > _bb.remaining()) {
      expandByteBuffer(ary.length*4); // Try to expand BB buffer to fit input array
    }
    int sofar = 0;
    while( sofar < ary.length ) {
      IntBuffer ib = _bb.asIntBuffer();
      int len = Math.min(ary.length - sofar, ib.remaining());
      ib.put(ary, sofar, len);
      sofar += len;
      _bb.position(_bb.position() + ib.position()*4);
      if( sofar < ary.length ) sendPartial();
    }
    return this;
  }
  public AutoBuffer putA8( long[] ary ) {
    //_arys++;
    if( ary == null ) return putInt(-1);
    // Trim leading & trailing zeros.  Pass along the length of leading &
    // trailing zero sections, and the non-zero section in the middle.
    int x=0; for( ; x<ary.length; x++ ) if( ary[x  ]!=0 ) break;
    int y=ary.length; for( ; y>x; y-- ) if( ary[y-1]!=0 ) break;
    int nzlen = y-x;
    putInt(x);
    putInt(nzlen);
    if( nzlen > 0 )             // If any trailing nulls
      putInt(ary.length-y);     // Trailing zeros

    // Size trim the NZ section: pass as bytes or shorts if possible.
    long min=Long.MAX_VALUE, max=Long.MIN_VALUE;
    for( int i=x; i<y; i++ ) { if( ary[i]<min ) min=ary[i]; if( ary[i]>max ) max=ary[i]; }
    if( 0 <= min && max < 256 ) { // Ship as unsigned bytes
      put1(1);  for( int i=x; i<y; i++ ) put1((int)ary[i]);
      return this;
    }
    if( Short.MIN_VALUE <= min && max < Short.MAX_VALUE ) { // Ship as shorts
      put1(2);  for( int i=x; i<y; i++ ) put2((short)ary[i]);
      return this;
    }
    if( Integer.MIN_VALUE <= min && max < Integer.MAX_VALUE ) { // Ship as ints
      put1(4);  for( int i=x; i<y; i++ ) put4((int)ary[i]);
      return this;
    }

    put1(8);                    // Ship as full longs
    int sofar = x;
    if ((y-sofar)*8 > _bb.remaining()) expandByteBuffer(ary.length*8);
    while( sofar < y ) {
      LongBuffer lb = _bb.asLongBuffer();
      int len = Math.min(y - sofar, lb.remaining());
      lb.put(ary, sofar, len);
      sofar += len;
      _bb.position(_bb.position() + lb.position() * 8);
      if( sofar < y ) sendPartial();
    }
    return this;
  }
  public AutoBuffer putA4f( float[] ary ) {
    //_arys++;
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
    if (ary.length*4 > _bb.remaining()) expandByteBuffer(ary.length*4);
    int sofar = 0;
    while( sofar < ary.length ) {
      FloatBuffer fb = _bb.asFloatBuffer();
      int len = Math.min(ary.length - sofar, fb.remaining());
      fb.put(ary, sofar, len);
      sofar += len;
      _bb.position(_bb.position() + fb.position()*4);
      if( sofar < ary.length ) sendPartial();
    }
    return this;
  }
  public AutoBuffer putA8d( double[] ary ) {
    //_arys++;
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
    if (ary.length*8 > _bb.remaining()) expandByteBuffer(ary.length*8);
    int sofar = 0;
    while( sofar < ary.length ) {
      DoubleBuffer db = _bb.asDoubleBuffer();
      int len = Math.min(ary.length - sofar, db.remaining());
      db.put(ary, sofar, len);
      sofar += len;
      _bb.position(_bb.position() + db.position()*8);
      if( sofar < ary.length ) sendPartial();
    }
    return this;
  }

  public AutoBuffer putAA1( byte[][] ary ) {
    //_arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA1(ary[i]);
    return this;
  }
  @SuppressWarnings("unused")  AutoBuffer putAA2( short[][] ary ) {
    //_arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA2(ary[i]);
    return this;
  }
  public AutoBuffer putAA4( int[][] ary ) {
    //_arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA4(ary[i]);
    return this;
  }
  @SuppressWarnings("unused")
  public AutoBuffer putAA4f( float[][] ary ) {
    //_arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA4f(ary[i]);
    return this;
  }
  public AutoBuffer putAA8( long[][] ary ) {
    //_arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA8(ary[i]);
    return this;
  }
  @SuppressWarnings("unused")  public AutoBuffer putAA8d( double[][] ary ) {
    //_arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA8d(ary[i]);
    return this;
  }
  public AutoBuffer putAAA4( int[][][] ary ) {
    //_arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putAA4(ary[i]);
    return this;
  }
  public AutoBuffer putAAA8( long[][][] ary ) {
    //_arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putAA8(ary[i]);
    return this;
  }
  public AutoBuffer putAAA8d( double[][][] ary ) {
    //_arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putAA8d(ary[i]);
    return this;
  }
  // Put a String as bytes (not chars!)
  public AutoBuffer putStr( String s ) {
    if( s==null ) return putInt(-1);
    return putA1(StringUtils.bytesOf(s));
  }

  @SuppressWarnings("unused")  public AutoBuffer putEnum( Enum x ) {
    return put1(x==null ? -1 : x.ordinal());
  }

  public static byte[] javaSerializeWritePojo(Object o) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream out = null;
    try {
      out = new ObjectOutputStream(bos);
      out.writeObject(o);
      out.close();
      return bos.toByteArray();
    } catch (IOException e) {
      throw Log.throwErr(e);
    }
  }

  public static Object javaSerializeReadPojo(byte [] bytes) {
    try {
      final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
      Object o = ois.readObject();
      return o;
    } catch (IOException e) {
      String className = nameOfClass(bytes);
      throw Log.throwErr(new RuntimeException("Failed to deserialize " + className, e));
    } catch (ClassNotFoundException e) {
      throw Log.throwErr(e);
    }
  }

  static String nameOfClass(byte[] bytes) {
    if (bytes == null) return "(null)";
    if (bytes.length < 11) return "(no name)";

    int nameSize = Math.min(40, Math.max(3, bytes[7]));
    return new String(bytes, 8, Math.min(nameSize, bytes.length - 8));
  }
  // ==========================================================================
  // Java Serializable objects
  // Note: These are heck-a-lot more expensive than their Freezable equivalents.

  @SuppressWarnings("unused") public AutoBuffer putSer( Object obj ) {
    if (obj == null) return putA1(null);
    return putA1(javaSerializeWritePojo(obj));
  }

  @SuppressWarnings("unused") public AutoBuffer putASer(Object[] fs) {
    //_arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putSer(fs[i]);
    return this;
  }

  @SuppressWarnings("unused") public AutoBuffer putAASer(Object[][] fs) {
    //_arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putASer(fs[i]);
    return this;
  }

  @SuppressWarnings("unused") public AutoBuffer putAAASer(Object[][][] fs) {
    //_arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putAASer(fs[i]);
    return this;
  }

  @SuppressWarnings("unused") public Object getSer() {
    byte[] ba = getA1();
    return ba == null ? null : javaSerializeReadPojo(ba);
  }

  @SuppressWarnings("unused") public <T> T getSer(Class<T> tc) {
    return (T)getSer();
  }

  @SuppressWarnings("unused") public <T> T[] getASer(Class<T> tc) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    T[] ts = (T[]) Array.newInstance(tc, x+y+z);
    for( int i = x; i < x+y; ++i ) ts[i] = getSer(tc);
    return ts;
  }

  @SuppressWarnings("unused") public <T> T[][] getAASer(Class<T> tc) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    T[][] ts = (T[][]) Array.newInstance(tc, x+y+z);
    for( int i = x; i < x+y; ++i ) ts[i] = getASer(tc);
    return ts;
  }

  @SuppressWarnings("unused") public <T> T[][][] getAAASer(Class<T> tc) {
    //_arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    T[][][] ts = (T[][][]) Array.newInstance(tc, x+y+z);
    for( int i = x; i < x+y; ++i ) ts[i] = getAASer(tc);
    return ts;
  }

  // ==========================================================================
  // JSON AutoBuffer printers

  public AutoBuffer putJNULL( ) { return put1('n').put1('u').put1('l').put1('l'); }
  // Escaped JSON string
  private AutoBuffer putJStr( String s ) {
    byte[] b = StringUtils.bytesOf(s);
    int off=0;
    for( int i=0; i<b.length; i++ ) {
      if( b[i] == '\\' || b[i] == '"') { // Double up backslashes, escape quotes
        putA1(b,off,i);         // Everything so far (no backslashes)
        put1('\\');             // The extra backslash
        off=i;                  // Advance the "so far" variable
      }
      // Handle remaining special cases in JSON
      // if( b[i] == '/' ) { putA1(b,off,i); put1('\\'); put1('/'); off=i+1; continue;}
      if( b[i] == '\b' ) { putA1(b,off,i); put1('\\'); put1('b'); off=i+1; continue;}
      if( b[i] == '\f' ) { putA1(b,off,i); put1('\\'); put1('f'); off=i+1; continue;}
      if( b[i] == '\n' ) { putA1(b,off,i); put1('\\'); put1('n'); off=i+1; continue;}
      if( b[i] == '\r' ) { putA1(b,off,i); put1('\\'); put1('r'); off=i+1; continue;}
      if( b[i] == '\t' ) { putA1(b,off,i); put1('\\'); put1('t'); off=i+1; continue;}
      // ASCII Control characters
      if( b[i] == 127 ) { putA1(b,off,i); put1('\\'); put1('u'); put1('0'); put1('0'); put1('7'); put1('f'); off=i+1; continue;}
      if( b[i] >= 0 && b[i] < 32 ) {
        String hexStr = Integer.toHexString(b[i]);
        putA1(b, off, i); put1('\\'); put1('u');
        for (int j = 0; j < 4 - hexStr.length(); j++) put1('0');
        for (int j = 0; j < hexStr.length(); j++) put1(hexStr.charAt(hexStr.length()-j-1));
        off=i+1;
      }
    }
    return putA1(b,off,b.length);
  }
  public AutoBuffer putJSONStrUnquoted ( String s ) { return s==null ? putJNULL() : putJStr(s); }
  public AutoBuffer putJSONStrUnquoted ( String name, String s ) { return s==null ? putJSONStr(name).put1(':').putJNULL() : putJSONStr(name).put1(':').putJStr(s); }

  public AutoBuffer putJSONName( String s ) { return put1('"').putJStr(s).put1('"'); }
  public AutoBuffer putJSONStr ( String s ) { return s==null ? putJNULL() : putJSONName(s); }
  public AutoBuffer putJSONAStr(String[] ss) {
    if( ss == null ) return putJNULL();
    put1('[');
    for( int i=0; i<ss.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONStr(ss[i]);
    }
    return put1(']');
  }
  private AutoBuffer putJSONAAStr( String[][] sss) {
    if( sss == null ) return putJNULL();
    put1('[');
    for( int i=0; i<sss.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONAStr(sss[i]);
    }
    return put1(']');
  }

  @SuppressWarnings("unused")  public AutoBuffer putJSONStr  (String name, String   s   ) { return putJSONStr(name).put1(':').putJSONStr(s); }
  @SuppressWarnings("unused")  public AutoBuffer putJSONAStr (String name, String[] ss  ) { return putJSONStr(name).put1(':').putJSONAStr(ss); }
  @SuppressWarnings("unused")  public AutoBuffer putJSONAAStr(String name, String[][]sss) { return putJSONStr(name).put1(':').putJSONAAStr(sss); }

  @SuppressWarnings("unused")  public AutoBuffer putJSONSer   (String name, Object o         ) { return putJSONStr(name).put1(':').putJNULL(); }
  @SuppressWarnings("unused")  public AutoBuffer putJSONASer  (String name, Object[] oo      ) { return putJSONStr(name).put1(':').putJNULL(); }
  @SuppressWarnings("unused")  public AutoBuffer putJSONAASer (String name, Object[][] ooo   ) { return putJSONStr(name).put1(':').putJNULL(); }
  @SuppressWarnings("unused")  public AutoBuffer putJSONAAASer(String name, Object[][][] oooo) { return putJSONStr(name).put1(':').putJNULL(); }

  public AutoBuffer putJSONAZ( String name, boolean[] f) { return putJSONStr(name).put1(':').putJSONAZ(f); }

  public AutoBuffer putJSON(Freezable ice) { return ice == null ? putJNULL() : ice.writeJSON(this); }
  public AutoBuffer putJSONA( Freezable fs[]  ) {
    if( fs == null ) return putJNULL();
    put1('[');
    for( int i=0; i<fs.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON(fs[i]);
    }
    return put1(']');
  }
  public AutoBuffer putJSONAA( Freezable fs[][]) {
    if( fs == null ) return putJNULL();
    put1('[');
    for( int i=0; i<fs.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA(fs[i]);
    }
    return put1(']');
  }

  public AutoBuffer putJSONAAA( Freezable fs[][][]) {
    if( fs == null ) return putJNULL();
    put1('[');
    for( int i=0; i<fs.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONAA(fs[i]);
    }
    return put1(']');
  }

  @SuppressWarnings("unused")  public AutoBuffer putJSON  ( String name, Freezable f   ) { return putJSONStr(name).put1(':').putJSON  (f); }
  public AutoBuffer putJSONA ( String name, Freezable f[] ) { return putJSONStr(name).put1(':').putJSONA (f); }
  @SuppressWarnings("unused")  public AutoBuffer putJSONAA( String name, Freezable f[][]){ return putJSONStr(name).put1(':').putJSONAA(f); }

  @SuppressWarnings("unused")  public AutoBuffer putJSONAAA( String name, Freezable f[][][]){ return putJSONStr(name).put1(':').putJSONAAA(f); }

  @SuppressWarnings("unused")  public AutoBuffer putJSONZ( String name, boolean value ) { return putJSONStr(name).put1(':').putJStr("" + value); }

  private AutoBuffer putJSONAZ(boolean [] b) {
    if (b == null) return putJNULL();
    put1('[');
    for( int i = 0; i < b.length; ++i) {
      if (i > 0) put1(',');
      putJStr(""+b[i]);
    }
    return put1(']');
  }


  // Most simple integers
  private AutoBuffer putJInt( int i ) {
    byte b[] = StringUtils.toBytes(i);
    return putA1(b,b.length);
  }

  public AutoBuffer putJSON1( byte b ) { return putJInt(b); }
  public AutoBuffer putJSONA1( byte ary[] ) {
    if( ary == null ) return putJNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON1(ary[i]);
    }
    return put1(']');
  }
  private AutoBuffer putJSONAA1(byte ary[][]) {
    if( ary == null ) return putJNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA1(ary[i]);
    }
    return put1(']');
  }
  @SuppressWarnings("unused")  public AutoBuffer putJSON1  (String name, byte b    ) { return putJSONStr(name).put1(':').putJSON1(b); }
  @SuppressWarnings("unused")  public AutoBuffer putJSONA1 (String name, byte b[]  ) { return putJSONStr(name).put1(':').putJSONA1(b); }
  @SuppressWarnings("unused")  public AutoBuffer putJSONAA1(String name, byte b[][]) { return putJSONStr(name).put1(':').putJSONAA1(b); }

  public AutoBuffer putJSONAEnum(String name, Enum[] enums) {
    return putJSONStr(name).put1(':').putJSONAEnum(enums);
  }
  public AutoBuffer putJSONAEnum( Enum[] enums ) {
    if( enums == null ) return putJNULL();
    put1('[');
    for( int i=0; i<enums.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONEnum(enums[i]);
    }
    return put1(']');
  }


  AutoBuffer putJSON2( char c ) { return putJSON4(c); }
  AutoBuffer putJSON2( String name, char c ) { return putJSONStr(name).put1(':').putJSON2(c); }
  AutoBuffer putJSON2( short c ) { return putJSON4(c); }
  AutoBuffer putJSON2( String name, short c ) { return putJSONStr(name).put1(':').putJSON2(c); }
  public AutoBuffer putJSONA2( String name, short ary[] ) { return putJSONStr(name).put1(':').putJSONA2(ary); }
  AutoBuffer putJSONA2( short ary[] ) {
    if( ary == null ) return putJNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON2(ary[i]);
    }
    return put1(']');
  }

  AutoBuffer putJSON8 ( long l ) { return putJStr(Long.toString(l)); }
  AutoBuffer putJSONA8( long ary[] ) {
    if( ary == null ) return putJNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON8(ary[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONAA8( long ary[][] ) {
    if( ary == null ) return putJNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA8(ary[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONAAA8( long ary[][][] ) {
    if( ary == null ) return putJNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONAA8(ary[i]);
    }
    return put1(']');
  }

  AutoBuffer putJSONEnum( Enum e ) {
    return e==null ? putJNULL() : put1('"').putJStr(e.toString()).put1('"');
  }

  public AutoBuffer putJSON8 ( String name, long l   ) { return putJSONStr(name).put1(':').putJSON8(l); }
  public AutoBuffer putJSONEnum( String name, Enum e ) { return putJSONStr(name).put1(':').putJSONEnum(e); }

  public AutoBuffer putJSONA8( String name, long ary[] ) { return putJSONStr(name).put1(':').putJSONA8(ary); }
  public AutoBuffer putJSONAA8( String name, long ary[][] ) { return putJSONStr(name).put1(':').putJSONAA8(ary); }
  public AutoBuffer putJSONAAA8( String name, long ary[][][] ) { return putJSONStr(name).put1(':').putJSONAAA8(ary); }

  public AutoBuffer putJSONZ(boolean b) { return putJStr(Boolean.toString(b)); }

  public AutoBuffer putJSON4(int i) { return putJStr(Integer.toString(i)); }
  AutoBuffer putJSONA4( int[] a) {
    if( a == null ) return putJNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON4(a[i]);
    }
    return put1(']');
  }

  AutoBuffer putJSONAA4( int[][] a ) {
    if( a == null ) return putJNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA4(a[i]);
    }
    return put1(']');
  }

  AutoBuffer putJSONAAA4( int[][][] a ) {
    if( a == null ) return putJNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONAA4(a[i]);
    }
    return put1(']');
  }

  public AutoBuffer putJSON4 ( String name, int i ) { return putJSONStr(name).put1(':').putJSON4(i); }
  public AutoBuffer putJSONA4( String name, int[] a) { return putJSONStr(name).put1(':').putJSONA4(a); }
  public AutoBuffer putJSONAA4( String name, int[][] a ) { return putJSONStr(name).put1(':').putJSONAA4(a); }
  public AutoBuffer putJSONAAA4( String name, int[][][] a ) { return putJSONStr(name).put1(':').putJSONAAA4(a); }

  public AutoBuffer putJSON4f ( float f ) { return f==Float.POSITIVE_INFINITY?putJSONStr(JSON_POS_INF):(f==Float.NEGATIVE_INFINITY?putJSONStr(JSON_NEG_INF):(Float.isNaN(f)?putJSONStr(JSON_NAN):putJStr(Float .toString(f)))); }
  public AutoBuffer putJSON4f ( String name, float f ) { return putJSONStr(name).put1(':').putJSON4f(f); }
  AutoBuffer putJSONA4f( float[] a ) {
    if( a == null ) return putJNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON4f(a[i]);
    }
    return put1(']');
  }
  public AutoBuffer putJSONA4f(String name, float[] a) {
    putJSONStr(name).put1(':');
    return putJSONA4f(a);
  }
  AutoBuffer putJSONAA4f(String name, float[][] a) {
    putJSONStr(name).put1(':');
    if( a == null ) return putJNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA4f(a[i]);
    }
    return put1(']');
  }

  public AutoBuffer putJSON8d( double d ) {
    if (TwoDimTable.isEmpty(d)) return putJNULL();
    return d==Double.POSITIVE_INFINITY?putJSONStr(JSON_POS_INF):(d==Double.NEGATIVE_INFINITY?putJSONStr(JSON_NEG_INF):(Double.isNaN(d)?putJSONStr(JSON_NAN):putJStr(Double.toString(d))));
  }
  public AutoBuffer putJSON8d( String name, double d ) { return putJSONStr(name).put1(':').putJSON8d(d); }
  public AutoBuffer putJSONA8d( String name, double[] a ) {
    return putJSONStr(name).put1(':').putJSONA8d(a);
  }
  public AutoBuffer putJSONAA8d( String name, double[][] a) {
    return putJSONStr(name).put1(':').putJSONAA8d(a);
  }
  public AutoBuffer putJSONAAA8d( String name, double[][][] a) { return putJSONStr(name).put1(':').putJSONAAA8d(a); }

  public AutoBuffer putJSONA8d( double[] a ) {
    if( a == null ) return putJNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON8d(a[i]);
    }
    return put1(']');
  }

  public AutoBuffer putJSONAA8d( double[][] a ) {
    if( a == null ) return putJNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA8d(a[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONAAA8d( double ary[][][] ) {
    if( ary == null ) return putJNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONAA8d(ary[i]);
    }
    return put1(']');
  }

  static final String JSON_NAN = "NaN";
  static final String JSON_POS_INF = "Infinity";
  static final String JSON_NEG_INF = "-Infinity";
}
