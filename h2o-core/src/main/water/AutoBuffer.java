package water;

import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Random;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import water.util.Log;

/**
 * A ByteBuffer backed mixed Input/OutputStream class.
 *
 * Reads/writes empty/fill the ByteBuffer as needed.  When it is empty/full it
 * we go to the ByteChannel for more/less.  Because DirectByteBuffers are
 * expensive to make, we keep a few pooled.
 *
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 */
public final class AutoBuffer {
  // The direct ByteBuffer for schlorping data about
  ByteBuffer _bb;

  // The ByteChannel for schlorping more data in or out.  Could be a
  // SocketChannel (for a TCP connection) or a FileChannel (spill-to-disk) or a
  // DatagramChannel (for a UDP connection).
  private ByteChannel _chan;

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
  // them when flipping the ByteBuffer to the next chunk of data.
  private boolean _firstPage;

  // Total size written out from 'new' to 'close'.  Only updated when actually
  // reading or writing data, or after close().  For profiling only.
  int _size, _zeros, _arys;
  // More profiling: start->close msec, plus nano's spent in blocking I/O
  // calls.  The difference between (close-start) and i/o msec is the time the
  // i/o thread spends doing other stuff (e.g. allocating Java objects or
  // (de)serializing).
  long _time_start_ms, _time_close_ms, _time_io_ns;
  // I/O persistence flavor: Value.ICE, NFS, HDFS, S3, TCP
  final byte _persist;

  // The assumed max UDP packetsize
  static final int MTU = 1500-8/*UDP packet header size*/;

  // Enable this to test random TCP fails on open or write
  static final Random RANDOM_TCP_DROP = null; //new Random();

  // Incoming UDP request.  Make a read-mode AutoBuffer from the open Channel,
  // figure the originating H2ONode from the first few bytes read.
  AutoBuffer( DatagramChannel sock ) throws IOException {
    _chan = null;
    _bb = bbMake();
    _read = true;               // Reading by default
    _firstPage = true;
    // Read a packet; can get H2ONode from 'sad'?
    Inet4Address addr = null;
    SocketAddress sad = sock.receive(_bb);
    if( sad instanceof InetSocketAddress ) {
      InetAddress address = ((InetSocketAddress) sad).getAddress();
      if( address instanceof Inet4Address ) {
        addr = (Inet4Address) address;
      }
    }
    _size = _bb.position();
    _bb.flip();                 // Set limit=amount read, and position==0

    if( addr == null ) throw new RuntimeException("Unhandled socket type: " + sad);
    // Read Inet from socket, port from the stream, figure out H2ONode
    _h2o = H2ONode.intern(addr, getPort());
    _firstPage = true;
    assert _h2o != null;
    _persist = 0;               // No persistance
  }

  // Incoming TCP request.  Make a read-mode AutoBuffer from the open Channel,
  // figure the originating H2ONode from the first few bytes read.
  AutoBuffer( SocketChannel sock ) throws IOException {
    _chan = sock;
    raisePriority();            // Make TCP priority high
    _bb = bbMake();
    _bb.flip();
    _read = true;               // Reading by default
    _firstPage = true;
    // Read Inet from socket, port from the stream, figure out H2ONode
    _h2o = H2ONode.intern(sock.socket().getInetAddress(), getPort());
    _firstPage = true;          // Yes, must reset this.
    assert _h2o != null && _h2o != H2O.SELF;
    _time_start_ms = System.currentTimeMillis();
    _persist = Value.TCP;
  }

  // Make an AutoBuffer to write to an H2ONode.  Requests for full buffer will
  // open a TCP socket and roll through writing to the target.  Smaller
  // requests will send via UDP.
  AutoBuffer( H2ONode h2o ) {
    _bb = bbMake();
    _chan = null;               // Channel made lazily only if we write alot
    _h2o = h2o;
    _read = false;              // Writing by default
    _firstPage = true;          // Filling first page
    assert _h2o != null;
    _time_start_ms = System.currentTimeMillis();
    _persist = Value.TCP;
  }

  // Spill-to/from-disk request.
  public AutoBuffer( FileChannel fc, boolean read, byte persist ) {
    _bb = bbMake();
    _chan = fc;                 // Write to read/write
    _h2o = null;                // File Channels never have an _h2o
    _read = read;               // Mostly assert reading vs writing
    if( read ) _bb.flip();
    _time_start_ms = System.currentTimeMillis();
    _persist = persist;         // One of Value.ICE, NFS, S3, HDFS
  }

  // Read from UDP multicast.  Same as the byte[]-read variant, except there is an H2O.
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

  /** Read from a fixed byte[]; should not be closed. */
  AutoBuffer( byte[] buf ) { this(buf,0); }
  /** Read from a fixed byte[]; should not be closed. */
  AutoBuffer( byte[] buf, int off ) {
    assert buf != null : "null fed to ByteBuffer.wrap";
    _bb = ByteBuffer.wrap(buf).order(ByteOrder.nativeOrder());
    _bb.position(off);
    _chan = null;
    _h2o = null;
    _read = true;
    _firstPage = true;
    _persist = 0;               // No persistance
  }

  /**  Write to an ever-expanding byte[].  Instead of calling {@link #close()},
   *  call {@link #buf()} to retrieve the final byte[].
   */
  AutoBuffer( ) {
    _bb = ByteBuffer.wrap(new byte[16]).order(ByteOrder.nativeOrder());
    _chan = null;
    _h2o = null;
    _read = false;
    _firstPage = true;
    _persist = 0;               // No persistance
  }

  /** Write to a known sized byte[].  Instead of calling close(), call
   * {@link #bufClose()} to retrieve the final byte[].
   */
  AutoBuffer( int len ) {
    _bb = ByteBuffer.wrap(MemoryManager.malloc1(len)).order(ByteOrder.nativeOrder());
    _chan = null;
    _h2o = null;
    _read = false;
    _firstPage = true;
    _persist = 0;               // No persistance
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
  private static final boolean DEBUG = Boolean.getBoolean("h2o.find-ByteBuffer-leaks");
  private static final AtomicInteger BBMAKE = new AtomicInteger(0);
  private static final AtomicInteger BBFREE = new AtomicInteger(0);
  private static final AtomicInteger BBCACHE= new AtomicInteger(0);
  private static final LinkedBlockingDeque<ByteBuffer> BBS = new LinkedBlockingDeque<>();
  static final int BBSIZE = 64*1024; // Bytebuffer "common big size"
  private static void bbstats( AtomicInteger ai ) {
    if( !DEBUG ) return;
    if( (ai.incrementAndGet()&511)==511 ) {
      Log.warn("BB make="+BBMAKE.get()+" free="+BBFREE.get()+" cache="+BBCACHE.get()+" size="+BBS.size());
    }
  }

  private static ByteBuffer bbMake() {
    while( true ) {             // Repeat loop for DBB OutOfMemory errors
      ByteBuffer bb;
      try { bb = BBS.pollFirst(0,TimeUnit.SECONDS); }
      catch( InterruptedException e ) { throw Log.throwErr(e); }
      if( bb != null ) {
        bbstats(BBCACHE);
        return bb;
      }
      try {
        bb = ByteBuffer.allocateDirect(BBSIZE).order(ByteOrder.nativeOrder());
        bbstats(BBMAKE);
        return bb;
      } catch( OutOfMemoryError oome ) {
        // java.lang.OutOfMemoryError: Direct buffer memory
        if( !"Direct buffer memory".equals(oome.getMessage()) ) throw oome;
        System.out.println("Sleeping & retrying");
        try { Thread.sleep(100); } catch( InterruptedException ignore ) { }
      }
    }
  }
  private static void bbFree(ByteBuffer bb) {
    bbstats(BBFREE);
    bb.clear();
    BBS.offerFirst(bb);
  }

  private int bbFree() {
    if( _bb.isDirect() ) bbFree(_bb);
    _bb = null;
    return 0;                   // Flow-coding
  }

  // You thought TCP was a reliable protocol, right?  WRONG!  Fails 100% of the
  // time under heavy network load.  Connection-reset-by-peer & connection
  // timeouts abound, even after a socket open and after a 1st successful
  // ByteBuffer write.  It *appears* that the reader is unaware that a writer
  // was told "go ahead and write" by the TCP stack, so all these fails are
  // only on the writer-side.
  static class TCPIsUnreliableException extends RuntimeException {
    final IOException _ioe;
    TCPIsUnreliableException( IOException ioe ) { _ioe = ioe; }
  }

  // For reads, just assert all was read and close and release resources.
  // (release ByteBuffer back to the common pool).  For writes, force any final
  // bytes out.  If the write is to an H2ONode and is short, send via UDP.
  // AutoBuffer close calls order; i.e. a reader close() will block until the
  // writer does a close().
  public final int close() { return close(true,false); }
  final int close(boolean expect_tcp, boolean failed) {
    //if( _size > 2048 ) System.out.println("Z="+_zeros+" / "+_size+", A="+_arys);
    assert _h2o != null || _chan != null; // Byte-array backed should not be closed
    // Extra asserts on closing TCP channels: we should always know & expect
    // TCP channels, and read them fully.  If we close a TCP channel that is
    // not fully read then the writer will assert and we will silently run on.
    // Note: this assert is essentially redundant with the extra read/write of
    // 0xab below; closing a TCP read-channel early will read 1 more byte -
    // which probably will not be 0xab.
    final boolean tcp = hasTCP();
    assert !tcp    || expect_tcp; // If we have   TCP, we fully expect it
    assert !failed || expect_tcp; // If we failed TCP, we expect TCP
    try {
      if( _chan == null ) {     // No channel?
        if( _read ) return bbFree();
        // For small-packet write, send via UDP.  Since nothing is sent until
        // now, this close() call trivially orders - since the reader will not
        // even start (much less close()) until this packet is sent.
        if( _bb.position() < MTU ) return udpSend();
      }
      // Force AutoBuffer 'close' calls to order; i.e. block readers until
      // writers do a 'close' - by writing 1 more byte in the close-call which
      // the reader will have to wait for.
      if( tcp ) {               // TCP connection?
        if( failed ) {          // Failed TCP?
          try { _chan.close(); } catch( IOException ioe ) {} // Silently close
          if( !_read ) _h2o.freeTCPSocket(null); // Tell H2ONode socket is no longer available
        } else {                // Closing good TCP?
          if( _read ) {         // Reader?
            int x = get1U();    // Read 1 more byte
            assert x == 0xab : "AB.close instead of 0xab sentinel got "+x+", "+this;
            // Write the reader-handshake-byte.
            ((SocketChannel)_chan).socket().getOutputStream().write(0xcd);
            // do not close actually reader socket; recycle it in TCPReader thread
          } else {              // Writer?
            put1(0xab);         // Write one-more byte
            sendPartial();      // Finish partial writes
            // Read the writer-handshake-byte.
            SocketChannel sock = (SocketChannel)_chan;
            int x = sock.socket().getInputStream().read();
            // either TCP con was dropped or other side closed connection without reading/confirming (e.g. task was cancelled).
            if(x == -1)throw new TCPIsUnreliableException(new IOException("Other side closed connection unexpectedly."));
            assert x == 0xcd : "Handshake; writer expected a 0xcd from reader but got "+x;
            _h2o.freeTCPSocket(sock); // Recycle writable TCP channel
          }
        }
        restorePriority();      // And if we raised priority, lower it back
      } else {                  // FileChannel
        if( !_read ) sendPartial(); // Finish partial file-system writes
        _chan.close();
      }
      _time_close_ms = System.currentTimeMillis();
      TimeLine.record_IOclose(this,_persist); // Profile AutoBuffer connections
    } catch( IOException e ) {  // Dunno how to handle so crash-n-burn
      throw new TCPIsUnreliableException(e);
    }
    return bbFree();
  }

  // Need a sock for a big read or write operation.
  // See if we got one already, else open a new socket.
  private void tcpOpen() throws IOException {
    assert _firstPage && _bb.limit() >= 1+2+4; // At least something written
    assert _chan == null;
    assert _bb.position()==0;
    _chan = _h2o.getTCPSocket();
    raisePriority();
  }
  // Just close the channel here without reading anything. Without the task object at hand we do not know what (how many bytes) should
  // we read from the channel. And since the other side will try to read confirmation from us in before closing the channel,
  // we can not read till the end. So we just close the channel and let the other side to deal with it and figure out the task has been cancelled
  // (still sending ack ack back).
  void drainClose() {
    try {
      _chan.close();
      restorePriority();        // And if we raised priority, lower it back
      bbFree();
    } catch( IOException e ) {  // Dunno how to handle so crash-n-burn
      throw Log.throwErr(e);
    }
  }

  // True if we opened a TCP channel, or will open one to close-and-send
  boolean hasTCP() { return _chan instanceof SocketChannel || (_chan==null && _h2o!=null && _bb != null && _bb.position() >= MTU); }

  // True if we are in read-mode
  boolean readMode() { return _read; }
  // Size in bytes sent, after a close()
  int size() { return _size; }
  int zeros() { return _zeros; }

  // Available bytes in this buffer to read
  int remaining() { return _bb.remaining(); }
  int position () { return _bb.position (); }
  void position(int pos) { _bb.position(pos); }
  int limit() { return _bb.limit(); }

  // Return byte[] from a writable AutoBuffer
  final byte[] buf() {
    assert _h2o==null && _chan==null && !_read && !_bb.isDirect();
    return MemoryManager.arrayCopyOfRange(_bb.array(), _bb.arrayOffset(), _bb.position());
  }
  public final byte[] bufClose() {
    byte[] res = _bb.array();
    bbFree();
    return res;
  }
  final boolean eof() {
    assert _h2o==null && _chan==null;
    return _bb.position()==_bb.limit();
  }

  // For TCP sockets ONLY, raise the thread priority.  We assume we are
  // blocking other Nodes with our network I/O, so try to get the I/O
  // over with.
  private void raisePriority() {
    if(_oldPrior == -1){
      assert _chan instanceof SocketChannel;
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
    _size += _bb.position();
    _bb.flip();                 // Flip for sending
    if( _h2o==H2O.SELF ) {      // SELF-send is the multi-cast signal
      water.init.NetworkInit.multicast(_bb);
    } else {                    // Else single-cast send
      water.init.NetworkInit.CLOUD_DGRAM.send(_bb, _h2o._key);
    }
    return bbFree();
  }

  // Flip to write-mode
  AutoBuffer clearForWriting() {
    assert _read;
    _read = false;
    _bb.clear();
    _firstPage = true;
    return this;
  }
  // Flip to read-mode
  AutoBuffer flipForReading() {
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
    assert _chan != null : "Read to much data from a byte[] backed buffer, AB="+this;
    _bb.compact();            // Move remaining unread bytes to start of buffer; prep for reading
    // Its got to fit or we asked for too much
    assert _bb.position()+sz <= _bb.capacity() : "("+_bb.position()+"+"+sz+" <= "+_bb.capacity()+")";
    long ns = System.nanoTime();
    while( _bb.position() < sz ) { // Read until we got enuf
      try {
        int res = _chan.read(_bb); // Read more
        // Readers are supposed to be strongly typed and read the exact expected bytes.
        // However, if a TCP connection fails mid-read we'll get a short-read.
        // This is indistinguishable from a mis-alignment between the writer and reader!
        if( res == -1 )
          throw new TCPIsUnreliableException(new EOFException("Reading "+sz+" bytes, AB="+this));
        if( res ==  0 ) throw new RuntimeException("Reading zero bytes - so no progress?");
        _size += res;            // What we read
      } catch( IOException e ) { // Dunno how to handle so crash-n-burn
        // Linux/Ubuntu message for a reset-channel
        if( e.getMessage().equals("An existing connection was forcibly closed by the remote host") )
          throw new TCPIsUnreliableException(e);
        // Windows message for a reset-channel
        if( e.getMessage().equals("An established connection was aborted by the software in your host machine") )
          throw new TCPIsUnreliableException(e);
        throw Log.throwErr(e);
      }
    }
    _time_io_ns += (System.nanoTime()-ns);
    _bb.flip();                 // Prep for handing out bytes
    //for( int i=0; i < _bb.limit(); i++ ) if( _bb.get(i)==0 ) _zeros++;
    _firstPage = false;         // First page of data is gone gone gone
    return _bb;
  }

  /** Put as needed to keep from overflowing the ByteBuffer. */
  private ByteBuffer putSp( int sz ) {
    assert !_read;
    if( sz <= _bb.remaining() ) return _bb;
    return sendPartial();
  }
  // Do something with partial results, because the ByteBuffer is full.
  // If we are byte[] backed, double the backing array size.
  // If we are doing I/O, ship the bytes we have now and flip the ByteBuffer.
  private ByteBuffer sendPartial() {
    // Writing into an expanding byte[]?
    if( _h2o==null && _chan == null ) {
      // This is a byte[] backed buffer; expand the backing byte[].
      byte[] ary = _bb.array();
      int newlen = ary.length<<1; // New size is 2x old size
      int oldpos = _bb.position();
      _bb = ByteBuffer.wrap(MemoryManager.arrayCopyOfRange(ary,0,newlen),oldpos,newlen-oldpos)
        .order(ByteOrder.nativeOrder());
      return _bb;
    }
    // Doing I/O with the full ByteBuffer - ship partial results
    _size += _bb.position();
    if( _chan == null )
      TimeLine.record_send(this,true);
    _bb.flip(); // Prep for writing.
    try {
      if( _chan == null )
        tcpOpen(); // This is a big operation.  Open a TCP socket as-needed.
      //for( int i=0; i < _bb.limit(); i++ ) if( _bb.get(i)==0 ) _zeros++;
      long ns = System.nanoTime();
      while( _bb.hasRemaining() ) {
        _chan.write(_bb);
        if( RANDOM_TCP_DROP != null &&_chan instanceof SocketChannel && RANDOM_TCP_DROP.nextInt(100) == 0 )
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
      throw new TCPIsUnreliableException(e);
    }
    if( _bb.capacity() < 16*1024 ) _bb = bbMake();
    _firstPage = false;
    _bb.clear();
    return _bb;
  }

  int peek1() {
    if (eof())
      return 0;
    getSp(1);
    return get1U(position());
  }
  String getStr(int off, int len) {
    return new String(_bb.array(), _bb.arrayOffset()+off, len);
  }

  // -----------------------------------------------
  // Utility functions to get various Java primitives
  public boolean getZ() { return get1()!=0; }
  public byte   get1 () { return getSp(1).get      (); }
  public int    get1U() { return get1() & 0xFF;        }
  public char   get2 () { return getSp(2).getChar  (); }
  public int    get4 () { return getSp(4).getInt   (); }
  public float  get4f() { return getSp(4).getFloat (); }
  public long   get8 () { return getSp(8).getLong  (); }
  public double get8d() { return getSp(8).getDouble(); }


  int get3() {
    return get1U() << 0 |
           get1U() << 8 |
           get1U() << 16;
  }

  AutoBuffer put3( int x ) {
    assert (-1<<24) <= x && x < (1<<24);
    return put1((x >> 0)&0xFF).put1((x >> 8)&0xFF).put1(x >> 16);
  }


  int    get1U(int off) { return _bb.get (off)&0xFF; }
  char   get2 (int off) { return _bb.getChar  (off); }
  int    get4 (int off) { return _bb.getInt   (off); }
  float  get4f(int off) { return _bb.getFloat (off); }
  long   get8 (int off) { return _bb.getLong  (off); }
  double get8d(int off) { return _bb.getDouble(off); }

  AutoBuffer put1 (int off, int    v) { _bb.put      (off, (byte)(v&0xFF)); return this; }
  AutoBuffer put2 (int off, char   v) { _bb.putChar  (off, v);              return this; }
  AutoBuffer put2 (int off, short  v) { _bb.putShort (off, v);              return this; }
  AutoBuffer put4 (int off, int    v) { _bb.putInt   (off, v);              return this; }
  AutoBuffer put4f(int off, float  v) { _bb.putFloat (off, v);              return this; }
  AutoBuffer put8 (int off, long   v) { _bb.putLong  (off, v);              return this; }
  AutoBuffer put8d(int off, double v) { _bb.putDouble(off, v);              return this; }

  public AutoBuffer putZ (boolean b){ return put1(b?1:0); }
  public AutoBuffer put1 (   int b) { assert b >= -128 && b <= 255 : ""+b+" is not a byte";
                                      putSp(1).put((byte)b); return this; }
  public AutoBuffer put2 (  char c) { putSp(2).putChar  (c); return this; }
  public AutoBuffer put2 ( short s) { putSp(2).putShort (s); return this; }
  public AutoBuffer put4 (   int i) { putSp(4).putInt   (i); return this; }
  public AutoBuffer put4f( float f) { putSp(4).putFloat (f); return this; }
  public AutoBuffer put8 (  long l) { putSp(8).putLong  (l); return this; }
  public AutoBuffer put8d(double d) { putSp(8).putDouble(d); return this; }

  public AutoBuffer put(Freezable f) {
    if( f == null ) return put2(TypeMap.NULL);
    assert f.frozenType() > 0 : "No TypeMap for "+f.getClass().getName();
    put2((short)f.frozenType());
    return f.write(this);
  }
  AutoBuffer put(Iced f) {
    if( f == null ) return put2(TypeMap.NULL);
    assert f.frozenType() > 0;
    put2((short)f.frozenType());
    return f.write(this);
  }

  // Put a (compressed) integer.  Specifically values in the range -1 to ~250
  // will take 1 byte, values near a Short will take 1+2 bytes, values near an
  // Int will take 1+4 bytes, and bigger values 1+8 bytes.  This compression is
  // optimized for small integers (including -1 which is often used as a "array
  // is null" flag when passing the array length).
  AutoBuffer putInt( int x ) {
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


  public AutoBuffer putA(Iced[] fs) {
    _arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) put(fs[i]);
    return this;
  }
  public AutoBuffer putAA(Iced[][] fs) {
    _arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA(fs[i]);
    return this;
  }
  AutoBuffer putAAA(Iced[][][] fs) {
    _arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putAA(fs[i]);
    return this;
  }

  public <T extends Freezable> T get(Class<T> t) {
    short id = (short)get2();
    if( id == TypeMap.NULL ) return null;
    assert id > 0 : "Bad type id "+id;
    return TypeMap.newFreezable(id).read(this);
  }
  <T extends Iced> T get() {
    short id = (short)get2();
    if( id == TypeMap.NULL ) return null;
    assert id > 0 : "Bad type id "+id;
    return (T)TypeMap.newInstance(id).read(this);
  }
  public <T extends Iced> T[] getA(Class<T> tc) {
    _arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    T[] ts = (T[]) Array.newInstance(tc, x+y+z);
    for( int i = x; i < x+y; ++i ) ts[i] = get();
    return ts;
  }
  public <T extends Iced> T[][] getAA(Class<T> tc) {
    _arys++;
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
  <T extends Iced> T[][][] getAAA(Class<T> tc) {
    _arys++;
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
    _arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putStr(fs[i]);
    return this;
  }
  public String[] getAStr() {
    _arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    String[] ts = new String[x+y+z];
    for( int i = x; i < x+y; ++i ) ts[i] = getStr();
    return ts;
  }

  public AutoBuffer putAAStr(String[][] fs)    {
    _arys++;
    long xy = putZA(fs);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putAStr(fs[i]);
    return this;
  }
  public String[][] getAAStr() {
    _arys++;
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

  // Set the ctrl, port, task.  Ready to write more bytes afterwards
  AutoBuffer putUdp (UDP.udp type) {
    assert _bb.position()==0;
    putSp(1+2);
    _bb.put    ((byte)type.ordinal());
    _bb.putChar((char)H2O.H2O_PORT  ); // Outgoing port is always the sender's (me) port
    assert _bb.position()==1+2;
    return this;
  }

  AutoBuffer putTask(UDP.udp type, int tasknum) {
    return putUdp(type).put4(tasknum);
  }
  AutoBuffer putTask(int ctrl, int tasknum) {
    assert _bb.position()==0;
    putSp(1+2+4);
    _bb.put((byte)ctrl).putChar((char)H2O.H2O_PORT).putInt(tasknum);
    return this;
  }

  // -----------------------------------------------
  // Utility functions to read & write arrays
  public byte[] getA1( ) {
    _arys++;
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
    _arys++;
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
    _arys++;
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
    _arys++;
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
    _arys++;
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
    _arys++;
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
  public byte[][] getAA1( ) {
    _arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    byte[][] ary  = new byte[x+y+z][];
    for( int i=x; i<x+y; i++ ) ary[i] = getA1();
    return ary;
  }
  public short[][] getAA2( ) {
    _arys++;
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
    _arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    int[][] ary  = new int[x+y+z][];
    for( int i=x; i<x+y; i++ ) ary[i] = getA4();
    return ary;
  }
  public float[][] getAA4f( ) {
    _arys++;
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
    _arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    long[][] ary  = new long[x+y+z][];
    for( int i=x; i<x+y; i++ ) ary[i] = getA8();
    return ary;
  }
  public double[][] getAA8d( ) {
    _arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    double[][] ary  = new double[x+y+z][];
    for( int i=x; i<x+y; i++ ) ary[i] = getA8d();
    return ary;
  }
  public int[][][] getAAA4( ) {
    _arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    int[][][] ary  = new int[x+y+z][][];
    for( int i=x; i<x+y; i++ ) ary[i] = getAA4();
    return ary;
  }
  public long[][][] getAAA8( ) {
    _arys++;
    long xy = getZA();
    if( xy == -1 ) return null;
    int x=(int)(xy>>32);         // Leading nulls
    int y=(int)xy;               // Middle non-zeros
    int z = y==0 ? 0 : getInt(); // Trailing nulls
    long[][][] ary  = new long[x+y+z][][];
    for( int i=x; i<x+y; i++ ) ary[i] = getAA8();
    return ary;
  }

  public String getStr( ) {
    int len = getInt();
    return len == -1 ? null : new String(getA1(len));
  }

  public AutoBuffer putA1( byte[] ary ) {
    _arys++;
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
    return putA1(ary,ary.length);
  }
  public AutoBuffer putA1( byte[] ary, int length ) { return putA1(ary,0,length); }
  public AutoBuffer putA1( byte[] ary, int sofar, int length ) {
    while( sofar < length ) {
      int len = Math.min(length - sofar, _bb.remaining());
      _bb.put(ary, sofar, len);
      sofar += len;
      if( sofar < length ) sendPartial();
    }
    return this;
  }
  AutoBuffer putA2( short[] ary ) {
    _arys++;
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
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
    _arys++;
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
    int sofar = 0;
    while( sofar < ary.length ) {
      IntBuffer sb = _bb.asIntBuffer();
      int len = Math.min(ary.length - sofar, sb.remaining());
      sb.put(ary, sofar, len);
      sofar += len;
      _bb.position(_bb.position() + sb.position()*4);
      if( sofar < ary.length ) sendPartial();
    }
    return this;
  }
  public AutoBuffer putA8( long[] ary ) {
    _arys++;
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
    while( sofar < y ) {
      LongBuffer sb = _bb.asLongBuffer();
      int len = Math.min(y - sofar, sb.remaining());
      sb.put(ary, sofar, len);
      sofar += len;
      _bb.position(_bb.position() + sb.position()*8);
      if( sofar < y ) sendPartial();
    }
    return this;
  }
  AutoBuffer putA4f( float[] ary ) {
    _arys++;
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
    int sofar = 0;
    while( sofar < ary.length ) {
      FloatBuffer sb = _bb.asFloatBuffer();
      int len = Math.min(ary.length - sofar, sb.remaining());
      sb.put(ary, sofar, len);
      sofar += len;
      _bb.position(_bb.position() + sb.position()*4);
      if( sofar < ary.length ) sendPartial();
    }
    return this;
  }
  public AutoBuffer putA8d( double[] ary ) {
    _arys++;
    if( ary == null ) return putInt(-1);
    putInt(ary.length);
    int sofar = 0;
    while( sofar < ary.length ) {
      DoubleBuffer sb = _bb.asDoubleBuffer();
      int len = Math.min(ary.length - sofar, sb.remaining());
      sb.put(ary, sofar, len);
      sofar += len;
      _bb.position(_bb.position() + sb.position()*8);
      if( sofar < ary.length ) sendPartial();
    }
    return this;
  }

  AutoBuffer putAA1( byte[][] ary ) {
    _arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA1(ary[i]);
    return this;
  }
  AutoBuffer putAA2( short[][] ary ) {
    _arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA2(ary[i]);
    return this;
  }
  AutoBuffer putAA4( int[][] ary ) {
    _arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA4(ary[i]);
    return this;
  }
  AutoBuffer putAA4f( float[][] ary ) {
    _arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA4f(ary[i]);
    return this;
  }
  AutoBuffer putAA8( long[][] ary ) {
    _arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA8(ary[i]);
    return this;
  }
  AutoBuffer putAA8d( double[][] ary ) {
    _arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putA8d(ary[i]);
    return this;
  }
  AutoBuffer putAAA4( int[][][] ary ) {
    _arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putAA4(ary[i]);
    return this;
  }
  AutoBuffer putAAA8( long[][][] ary ) {
    _arys++;
    long xy = putZA(ary);
    if( xy == -1 ) return this;
    int x=(int)(xy>>32);
    int y=(int)xy;
    for( int i=x; i<x+y; i++ ) putAA8(ary[i]);
    return this;
  }
  // Put a String as bytes (not chars!)
  public AutoBuffer putStr( String s ) {
    if( s==null ) return putInt(-1);
    // Use the explicit getBytes instead of the default no-arg one, to avoid
    // the overhead of going in an out of a charset decoder.
    byte[] buf = MemoryManager.malloc1(s.length());
    s.getBytes(0,buf.length,buf,0);
    return putA1(buf);
  }

  public AutoBuffer putEnum( Enum x ) {
    return put1(x==null ? -1 : x.ordinal());
  }

  AutoBuffer copyArrayFrom(int offset, AutoBuffer ab, int abOff, int len) {
    byte[] dst = _bb.array();
    offset += _bb.arrayOffset();
    byte[] src = ab._bb.array();
    abOff += ab._bb.arrayOffset();
    System.arraycopy(src, abOff, dst, offset, len);
    _bb.position(_bb.position()+len); // Bump dest buffer offset
    return this;
  }

  void shift(int source, int target, int length) {
    System.arraycopy(_bb.array(), source, _bb.array(), target, length);
  }


  // ==========================================================================
  // JSON AutoBuffer printers

  AutoBuffer putStr2( String s ) {
    byte[] b = s.getBytes();
    int off=0;
    for( int i=0; i<b.length; i++ ) {
      if( b[i] == '\\' || b[i] == '"') { // Double up backslashes, escape quotes
        putA1(b,off,i);         // Everything so far (no backslashes)
        put1('\\');             // The extra backslash
        off=i;                  // Advance the "so far" variable
      }
      // Replace embedded newline & tab with quoted newlines
      if( b[i] == '\n' ) { putA1(b,off,i); put1('\\'); put1('n'); off=i+1; }
      if( b[i] == '\t' ) { putA1(b,off,i); put1('\\'); put1('t'); off=i+1; }
    }
    return putA1(b,off,b.length);
  }

  AutoBuffer putNULL( ) { return put1('n').put1('u').put1('l').put1('l'); }
  AutoBuffer putJSONStr( String s ) {
    return s==null ? putNULL() : put1('"').putStr2(s).put1('"');
  }
  AutoBuffer putJSONStr( String name, String value ) {
    return putJSONStr(name).put1(':').putJSONStr(value);
  }

  AutoBuffer putJSONAStr(String name, String[] fs) {
    putJSONStr(name).put1(':');
    return putJSONAStr(fs);
  }
  AutoBuffer putJSONAStr(String[] fs) {
    if( fs == null ) return putNULL();
    put1('[');
    for( int i=0; i<fs.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONStr(fs[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONAAStr( String name, String[][] a ) {
    putJSONStr(name).put1(':');
    if( a == null ) return putNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONAStr(a[i]);
    }
    return put1(']');
  }

  AutoBuffer putJSON( Iced ice ) {
    return ice == null ? putNULL() : ice.writeJSON(this);
  }
  AutoBuffer putJSONA( Iced fs[] ) {
    if( fs == null ) return putNULL();
    put1('[');
    for( int i=0; i<fs.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON(fs[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONAA( Iced fs[][] ) {
    if( fs == null ) return putNULL();
    put1('[');
    for( int i=0; i<fs.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA(fs[i]);
    }
    return put1(']');
  }

  AutoBuffer putJSONZ( String name, boolean value ) {
    putJSONStr(name).put1(':');
    putJSONStr("" + value);
    return this;
  }

  AutoBuffer putJSON1( byte b ) { return putJSON4(b); }
  public AutoBuffer putJSONA1( byte ary[] ) {
    if( ary == null ) return putNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON1(ary[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONAA1(byte ary[][]) {
    if( ary == null ) return putNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA1(ary[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONAA1(String name,byte ary[][]) {
    return putJSONStr(name).put1(':').putJSONAA1(ary);
  }

  AutoBuffer putJSON8 ( long l ) { return putStr2(Long.toString(l)); }
  AutoBuffer putJSONA8( long ary[] ) {
    if( ary == null ) return putNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON8(ary[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONAA8( long ary[][] ) {
    if( ary == null ) return putNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA8(ary[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONAAA8( long ary[][][] ) {
    if( ary == null ) return putNULL();
    put1('[');
    for( int i=0; i<ary.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONAA8(ary[i]);
    }
    return put1(']');
  }

  AutoBuffer putEnumJSON( Enum e ) {
    return e==null ? putNULL() : put1('"').putStr2(e.toString()).put1('"');
  }

  AutoBuffer putJSON  ( String name, Iced f   ) { return putJSONStr(name).put1(':').putJSON (f); }
  AutoBuffer putJSONA ( String name, Iced f[] ) { return putJSONStr(name).put1(':').putJSONA(f); }
  AutoBuffer putJSONAA( String name, Iced f[][]){ return putJSONStr(name).put1(':').putJSONAA(f); }
  AutoBuffer putJSON8 ( String name, long l   ) { return putJSONStr(name).put1(':').putJSON8(l); }
  AutoBuffer putEnumJSON( String name, Enum e ) { return putJSONStr(name).put1(':').putEnumJSON(e); }

  AutoBuffer putJSONA8( String name, long ary[] ) { return putJSONStr(name).put1(':').putJSONA8(ary); }
  AutoBuffer putJSONAA8( String name, long ary[][] ) { return putJSONStr(name).put1(':').putJSONAA8(ary); }
  AutoBuffer putJSONAAA8( String name, long ary[][][] ) { return putJSONStr(name).put1(':').putJSONAAA8(ary); }
  AutoBuffer putJSON4 ( int i ) { return putStr2(Integer.toString(i)); }
  AutoBuffer putJSON4 ( String name, int i ) { return putJSONStr(name).put1(':').putJSON4(i); }
  AutoBuffer putJSONA4( int[] a) {
    if( a == null ) return putNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON4(a[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONA4(String name, int[] a) {
    putJSONStr(name).put1(':');
    return putJSONA4(a);
  }
  AutoBuffer putJSONAA4(String name, int[][] a) {
    putJSONStr(name).put1(':');
    if( a == null ) return putNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA4(a[i]);
    }
    return put1(']');
  }

  AutoBuffer putJSON4f ( float f ) { return f==Float.POSITIVE_INFINITY?putJSONStr(JSON_POS_INF):(f==Float.NEGATIVE_INFINITY?putJSONStr(JSON_NEG_INF):(Float.isNaN(f)?putJSONStr(JSON_NAN):putStr2(Float .toString(f)))); }
  AutoBuffer putJSON4f ( String name, float f ) { return putJSONStr(name).put1(':').putJSON4f(f); }
  AutoBuffer putJSONA4f( float[] a ) {
    if( a == null ) return putNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON4f(a[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONA4f(String name, float[] a) {
    putJSONStr(name).put1(':');
    return putJSONA4f(a);
  }
  AutoBuffer putJSONAA4f(String name, float[][] a) {
    putJSONStr(name).put1(':');
    if( a == null ) return putNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA4f(a[i]);
    }
    return put1(']');
  }

  AutoBuffer putJSON8d( double d ) { return d==Double.POSITIVE_INFINITY?putJSONStr(JSON_POS_INF):(d==Double.NEGATIVE_INFINITY?putJSONStr(JSON_NEG_INF):(Double.isNaN(d)?putJSONStr(JSON_NAN):putStr2(Double.toString(d)))); }
  AutoBuffer putJSON8d( String name, double d ) { return putJSONStr(name).put1(':').putJSON8d(d); }
  AutoBuffer putJSONA8d( double[] a ) {
    if( a == null ) return putNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSON8d(a[i]);
    }
    return put1(']');
  }
  AutoBuffer putJSONA8d( String name, double[] a ) {
    putJSONStr(name).put1(':');
    return putJSONA8d(a);
  }
  AutoBuffer putJSONAA8d( String name, double[][] a ) {
    putJSONStr(name).put1(':');
    if( a == null ) return putNULL();
    put1('[');
    for( int i=0; i<a.length; i++ ) {
      if( i>0 ) put1(',');
      putJSONA8d(a[i]);
    }
    return put1(']');
  }

  static final String JSON_NAN = "NaN";
  static final String JSON_POS_INF = "Infinity";
  static final String JSON_NEG_INF = "-Infinity";
}
