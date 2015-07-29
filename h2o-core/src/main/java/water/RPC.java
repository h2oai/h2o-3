package water;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinPool;
import water.H2O.FJWThr;
import water.H2O.H2OCountedCompleter;
import water.UDP.udp;
import water.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A remotely executed FutureTask.  Flow is:
 *
 * 1- Build a DTask (or subclass).  This object will be replicated remotely.
 * 2- Make a RPC object, naming the target Node.  Call (re)call().  Call get()
 * to block for result, or cancel() or isDone(), etc.  Caller can also arrange
 * for caller.tryComplete() to be called in a F/J thread, to support completion
 * style execution (i.e. Continuation Passing Style).
 * 3- DTask will be serialized and sent to the target; small objects via UDP
 * and large via TCP (using AutoBuffer and auto-gen serializers).
 * 4- An RPC UDP control packet will be sent to target; this will also contain
 * the DTask if its small enough.
 * 4.5- The network may replicate (or drop) the UDP packet.  Dups may arrive.
 * 4.5- Sender may timeout, and send dup control UDP packets.
 * 5- Target will capture a UDP packet, and begin filtering dups (via task#).
 * 6- Target will deserialize the DTask, and call DTask.invoke() in a F/J thread.
 * 6.5- Target continues to filter (and drop) dup UDP sends (and timeout resends)
 * 7- Target finishes call, and puts result in DTask.
 * 8- Target serializes result and sends to back to sender.
 * 9- Target sends an ACK back (may be combined with the result if small enough)
 * 10- Target puts the ACK in H2ONode.TASKS for later filtering.
 * 10.5- Target receives dup UDP request, then replies with ACK back.
 * 11- Sender receives ACK result; deserializes; notifies waiters
 * 12- Sender sends ACKACK back
 * 12.5- Sender receives dup ACK's, sends dup ACKACK's back
 * 13- Target receives ACKACK, removes TASKS tracking
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

public class RPC<V extends DTask> implements Future<V>, Delayed, ForkJoinPool.ManagedBlocker {
  // The target remote node to pester for a response.  NULL'd out if the target
  // disappears or we cancel things (hence not final).
  H2ONode _target;

  // The distributed Task to execute.  Think: code-object+args while this RPC
  // is a call-in-progress (i.e. has an 'execution stack')
  final V _dt;

  // True if _dt contains the final answer
  volatile boolean _done;

  // True if the remote sent us a NACK (he received this RPC, but perhaps is
  // not done processing it, no need for more retries).
  volatile boolean _nack;

  // A locally-unique task number; a "cookie" handed to the remote process that
  // they hand back with the response packet.  These *never* repeat, so that we
  // can tell when a reply-packet points to e.g. a dead&gone task.
  int _tasknum;

  // Time we started this sucker up.  Controls re-send behavior.
  final long _started;
  long _retry;                  // When we should attempt a retry

  int _resendsCnt;

  // A list of CountedCompleters we will call tryComplete on when the RPC
  // finally completes.  Frequently null/zero.
  ArrayList<H2OCountedCompleter> _fjtasks;

  // We only send non-failing TCP info once; also if we used TCP it was large
  // so duplications are expensive.  However, we DO need to keep resending some
  // kind of "are you done yet?" UDP packet, incase the reply packet got dropped
  // (but also in case the main call was a single UDP packet and it got dropped).
  // Not volatile because read & written under lock.
  boolean _sentTcp;

  // To help with asserts, record the size of the sent DTask - if we resend
  // if should remain the same size.
  int _size;
  int _size_rez;                // Size of received results

  // Magic Cookies
  static final byte SERVER_UDP_SEND = 10;
  static final byte SERVER_TCP_SEND = 11;
  static final byte CLIENT_UDP_SEND = 12;
  static final byte CLIENT_TCP_SEND = 13;
  static final private String[] COOKIES = new String[] {
    "SERVER_UDP","SERVER_TCP","CLIENT_UDP","CLIENT_TCP" };


  final static int MAX_TIMEOUT = 5000; // 5 sec max timeout cap on exponential decay of retries

  public static <DT extends DTask> RPC<DT> call(H2ONode target, DT dtask) {
    return new RPC(target,dtask).call();
  }

  // Make a remotely executed FutureTask.  Must name the remote target as well
  // as the remote function.  This function is expected to be subclassed.
  public RPC( H2ONode target, V dtask ) {
    this(target,dtask,1.0f);
    setTaskNum();
  }
  // Only used for people who optimistically make RPCs that get thrown away and
  // never sent over the wire.  Split out task# generation from RPC <init> -
  // every task# MUST be sent over the wires, because the far end tracks the
  // task#'s in a dense list (no holes).
  RPC( H2ONode target, V dtask, float ignore ) {
    _target = target;
    _dt = dtask;
    _started = System.currentTimeMillis();
    _retry = RETRY_MS;
  }
  RPC<V> setTaskNum() {
    assert _tasknum == 0;
    _tasknum = _target.nextTaskNum();
    return this;
  }

  // Any Completer will not be carried over to remote; add it to the RPC call
  // so completion is signaled after the remote comes back.
  private void handleCompleter( CountedCompleter cc ) {
    assert cc instanceof H2OCountedCompleter;
    if( _fjtasks == null || !_fjtasks.contains(cc) )
      addCompleter((H2OCountedCompleter)cc);
    _dt.setCompleter(null);
  }

  // If running on self, just submit to queues & do locally
  private RPC<V> handleLocal() {
    assert _dt.getCompleter()==null;
    _dt.setCompleter(new H2O.H2OCallback<DTask>() {
        @Override public void callback(DTask dt) {
          synchronized(RPC.this) {
            _done = true;
            RPC.this.notifyAll();
          }
          doAllCompletions();
        }
        @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter dt) {
          synchronized(RPC.this) { // Might be called several times
            if( _done ) return true; // Filter down to 1st exceptional completion
            _dt.setException(ex);
            // must be the last set before notify call cause the waiting thread
            // can wake up at any moment independently on notify
            _done = true; 
            RPC.this.notifyAll();
          }
          doAllCompletions();
          return true;
        }
      });
    H2O.submitTask(_dt);
    return this;
  }

  private byte [] _bits;
  // Make an initial RPC, or re-send a packet.  Always called on 1st send; also
  // called on a timeout.

  public synchronized RPC<V> call() {
      // Any Completer will not be carried over to remote; add it to the RPC call
      // so completion is signaled after the remote comes back.
    _dt._rndBits = new Random().nextInt();
    CountedCompleter cc = _dt.getCompleter();
    if( cc != null )  handleCompleter(cc);

    // If running on self, just submit to queues & do locally
    if( _target==H2O.SELF ) return handleLocal();

    // Keep a global record, for awhile
    if( _target != null ) _target.taskPut(_tasknum,this);
    try {
      if( _nack ) return this; // Racing Nack rechecked under lock; no need to send retry
      // We could be racing timeouts-vs-replies.  Blow off timeout if we have an answer.
      if( isDone() ) {
        if( _target != null ) _target.taskRemove(_tasknum);
        return this;
      }
      // Default strategy: (re)fire the packet and (re)start the timeout.  We
      // "count" exactly 1 failure: just whether or not we shipped via TCP ever
      // once.  After that we fearlessly (re)send UDP-sized packets until the
      // server replies.

      // Pack classloader/class & the instance data into the outgoing
      // AutoBuffer.  If it fits in a single UDP packet, ship it.  If not,
      // finish off the current AutoBuffer (which is now going TCP style), and
      // make a new UDP-sized packet.  On a re-send of a TCP-sized hunk, just
      // send the basic UDP control packet.
      if( !_sentTcp ) {
        // Ship the UDP packet!
        while( true ) {         // Retry loop for broken TCP sends
          AutoBuffer ab = new AutoBuffer(_target);
          try {
            final boolean t;
            if(_bits != null){
              t = ab.putA1(_bits,_bits.length).hasTCP();
            } else {
              int offset = ab.position();
              ab.putTask(UDP.udp.exec, _tasknum).put1(CLIENT_UDP_SEND);
              ab.put(_dt);
              t = ab.hasTCP();
              if(_dt._modifiesInputs && !t)
                _bits = ab.copyRawBits(offset);
            }
            assert sz_check(ab) : "Resend of " + _dt.getClass() + " changes size from " + _size + " to " + ab.size() + " for task#" + _tasknum;
            ab.close();        // Then close; send final byte
            _sentTcp = t;  // Set after close (and any other possible fail)
            break;             // Break out of retry loop
          } catch( AutoBuffer.AutoBufferException e ) {
            Log.info("IOException during RPC call: " + e._ioe.getMessage() + ",  AB=" + ab + ", for task#" + _tasknum + ", waiting and retrying...");
            ab.drainClose();
            try { Thread.sleep(500); } catch (InterruptedException ignore) {}
          }
        } // end of while(true)
      } else {
        // Else it was sent via TCP in a prior attempt, and we've timed out.
        // This means the caller's ACK/answer probably got dropped and we need
        // him to resend it (or else the caller is still processing our
        // request).  Send a UDP reminder - but with the CLIENT_TCP_SEND flag
        // instead of the UDP send, and no DTask (since it previously went via
        // TCP, no need to resend it).
        AutoBuffer ab = new AutoBuffer(_target).putTask(UDP.udp.exec,_tasknum);
        ab.put1(CLIENT_TCP_SEND).close();
      }
      // Double retry until we exceed existing age.  This is the time to delay
      // until we try again.  Note that we come here immediately on creation,
      // so the first doubling happens before anybody does any waiting.  Also
      // note the generous 5sec cap: ping at least every 5 sec.
      _retry += (_retry < MAX_TIMEOUT ) ? _retry : MAX_TIMEOUT;
      // Put self on the "TBD" list of tasks awaiting Timeout.
      // So: dont really 'forget' but remember me in a little bit.
      UDPTimeOutThread.PENDING.add(this);
      return this;
    } catch( Throwable t ) {
      t.printStackTrace();
      throw Log.throwErr(t);
    }
  }

  private V result() {
    DException.DistributedException t = _dt.getDException();
    if( t != null ) throw t;
    return _dt;
  }
  // Similar to FutureTask.get() but does not throw any checked exceptions.
  // Returns null for canceled tasks, including those where the target dies.
  // Throws a DException if the remote throws, wrapping the original exception.
  @Override public V get() {
    // check priorities - FJ task can only block on a task with higher priority!
    Thread cThr = Thread.currentThread();
    int priority = (cThr instanceof FJWThr) ? ((FJWThr)cThr)._priority : -1;
    assert _dt.priority() > priority || (_dt.priority() == priority && _dt instanceof MRTask)
      : "*** Attempting to block on task (" + _dt.getClass() + ") with equal or lower priority. Can lead to deadlock! " + _dt.priority() + " <=  " + priority;
    if( _done ) return result(); // Fast-path shortcut, or throw if exception
    // Use FJP ManagedBlock for this blocking-wait - so the FJP can spawn
    // another thread if needed.
    try { ForkJoinPool.managedBlock(this); } catch( InterruptedException ignore ) { }
    if( _done ) return result(); // Fast-path shortcut or throw if exception
    assert isCancelled();
    return null;
  }
  // Return true if blocking is unnecessary, which is true if the Task isDone.
  @Override public boolean isReleasable() {  return isDone();  }
  // Possibly blocks the current thread.  Returns true if isReleasable would
  // return true.  Used by the FJ Pool management to spawn threads to prevent
  // deadlock is otherwise all threads would block on waits.
  @Override public synchronized boolean block() throws InterruptedException {
    while( !isDone() ) { wait(1000); }
    return true;
  }

  @Override public final V get(long timeout, TimeUnit unit) {
    if( _done ) return _dt;     // Fast-path shortcut
    throw H2O.fail();
  }

  // Done if target is dead or canceled, or we have a result.
  @Override public final boolean isDone() {  return _target==null || _done;  }
  // Done if target is dead or canceled
  @Override public final boolean isCancelled() { return _target==null; }
  // Attempt to cancel job
  @Override public final boolean cancel( boolean mayInterruptIfRunning ) {
    boolean did = false;
    synchronized(this) {        // Install the answer under lock
      if( !isCancelled() ) {
        did = true;             // Did cancel (was not cancelled already)
        _target.taskRemove(_tasknum);
        _target = null;         // Flag as canceled
        UDPTimeOutThread.PENDING.remove(this);
      }
      notifyAll();              // notify in any case
    }
    return did;
  }


  // ---
  // Handle the remote-side incoming UDP packet.  This is called on the REMOTE
  // Node, not local.  Wrong thread, wrong JVM.
  static class RemoteHandler extends UDP {
    @Override AutoBuffer call(AutoBuffer ab) { throw H2O.fail(); }
    // Pretty-print bytes 1-15; byte 0 is the udp_type enum
    @Override String print16( AutoBuffer ab ) {
      int flag = ab.getFlag();
      String clazz = (flag == CLIENT_UDP_SEND) ? TypeMap.className(ab.get2()) : "";
      return "task# "+ab.getTask()+" "+ clazz+" "+COOKIES[flag-SERVER_UDP_SEND];
    }
  }

  static class RPCCall extends H2OCountedCompleter implements Delayed {
    volatile DTask _dt; // Set on construction, atomically set to null onAckAck
    final H2ONode _client;
    final int _tsknum;
    long _started;              // Retry fields for the ackack
    long _retry;
    int _ackResendCnt;
    volatile boolean _computedAndReplied; // One time transition from false to true
    volatile boolean _computed; // One time transition from false to true
    // To help with asserts, record the size of the sent DTask - if we resend
    // if should remain the same size.  Also used for profiling.
    int _size;
    RPCCall(DTask dt, H2ONode client, int tsknum) {
      _dt = dt;
      _client = client;
      _tsknum = tsknum;
      if( _dt == null ) _computedAndReplied = true; // Only for Golden Completed Tasks (see H2ONode.java)
    }

    @Override protected void compute2() {
      // First set self to be completed when this subtask completer
      assert _dt.getCompleter() == null;
      _dt.setCompleter(this);
      // Run the remote task on this server...
      _dt.dinvoke(_client);
    }

    // When the task completes, ship results back to client.  F/J guarantees
    // that this is called only once with no onExceptionalCompletion calls - or
    // 1-or-more onExceptionalCompletion calls.
    @Override public void onCompletion( CountedCompleter caller ) {
      synchronized(this) {
        assert !_computed;
        _computed = true;
      }
      sendAck();
    }
    // Exception occurred when processing this task locally, set exception and
    // send it back to the caller.  Can be called lots of times (e.g., once per
    // MRTask.map call that throws).
    @Override public boolean onExceptionalCompletion( Throwable ex, CountedCompleter caller ) {
      if( _computed ) return false;
      synchronized(this) {    // Filter dup calls to onExCompletion
        if( _computed ) return false;
        _computed = true;
      }
      _dt.setException(ex);
      sendAck();
      return false;
    }

    private void sendAck() {
      // Send results back
      DTask dt, origDt = _dt; // _dt can go null the instant it is send over wire
      assert origDt!=null;    // Freed after completion
      while((dt = _dt) != null) { // Retry loop for broken TCP sends
        dt._rndBits = new Random().nextInt();
        AutoBuffer ab = null;
        try {
          // Start the ACK with results back to client.  If the client is
          // asking for a class/id mapping (or any job running at FETCH_ACK
          // priority) then return a udp.fetchack byte instead of a udp.ack.
          // The receiver thread then knows to handle the mapping at the higher
          // priority.
          UDP.udp udp = dt.priority()==H2O.FETCH_ACK_PRIORITY ? UDP.udp.fetchack : UDP.udp.ack;
          ab = new AutoBuffer(_client).putTask(udp,_tsknum).put1(SERVER_UDP_SEND);
          assert ab.position() == 2+1+2+4+1;
          dt.write(ab);         // Write the DTask - could be very large write
          dt._repliedTcp = ab.hasTCP(); // Resends do not need to repeat TCP result
          ab.close();                   // Then close; send final byte
          _computedAndReplied = true;   // After the final handshake, set computed+replied bit
          break;                        // Break out of retry loop
        } catch( AutoBuffer.AutoBufferException e ) {
          if( !_client._heartbeat._client ) // Report on servers only; clients allowed to be flaky
            Log.info("IOException during ACK, "+e._ioe.getMessage()+", t#"+_tsknum+" AB="+ab+", waiting and retrying...");
          ab.drainClose();
          if( _client._heartbeat._client ) // Dead client will not accept a TCP ACK response?
            this.CAS_DT(dt,null);          // cancel the ACK
          try { Thread.sleep(100); } catch (InterruptedException ignore) {}
        } catch( Exception e ) { // Custom serializer just barfed?
          Log.err(e);            // Log custom serializer exception
          ab.drainClose();
        }
      }  // end of while(true)
      if( dt == null )
        Log.info("Cancelled remote task#"+_tsknum+" "+origDt.getClass()+" to "+_client + " has been cancelled by remote");
      else {
        if( dt instanceof MRTask && dt.logVerbose() )
          Log.debug("Done remote task#"+_tsknum+" "+dt.getClass()+" to "+_client);
        _client.record_task_answer(this); // Setup for retrying Ack & AckAck, if not canceled
      }
    }


    // Re-send strictly the ack, because we're missing an AckAck
    final void resend_ack() {
      assert _computedAndReplied : "Found RPCCall not computed "+_tsknum;
      DTask dt = _dt;
      if( dt == null ) return;  // Received ACKACK already
      dt._rndBits = new Random().nextInt();
      UDP.udp udp = dt.priority()==H2O.FETCH_ACK_PRIORITY ? UDP.udp.fetchack : UDP.udp.ack;
      AutoBuffer rab = new AutoBuffer(_client).putTask(udp,_tsknum);
      boolean wasTCP = dt._repliedTcp;
      if( wasTCP )  rab.put1(RPC.SERVER_TCP_SEND) ; // Original reply sent via TCP
      else {
        rab.put1(RPC.SERVER_UDP_SEND); // Original reply sent via UDP
        assert rab.position() == 2+1+2+4+1;
        dt.write(rab);
      }
      assert sz_check(rab) : "Resend of " + _dt.getClass() + " changes size from "+_size+" to "+rab.size();
      assert dt._repliedTcp==wasTCP;
      rab.close();
      dt._repliedTcp = wasTCP;
      // Double retry until we exceed existing age.  This is the time to delay
      // until we try again.  Note that we come here immediately on creation,
      // so the first doubling happens before anybody does any waiting.  Also
      // note the generous 5sec cap: ping at least every 5 sec.
      _retry += (_retry < MAX_TIMEOUT ) ? _retry : MAX_TIMEOUT;
    }
    @Override protected byte priority() { return _dt.priority(); }
    // How long until we should do the "timeout" action?
    @Override public final long getDelay( TimeUnit unit ) {
      long delay = (_started+_retry)-System.currentTimeMillis();
      return unit.convert( delay, TimeUnit.MILLISECONDS );
    }
    // Needed for the DelayQueue API
    @Override public final int compareTo( Delayed t ) {
      RPCCall r = (RPCCall)t;
      long nextTime = _started+_retry, rNextTime = r._started+r._retry;
      return nextTime == rNextTime ? 0 : (nextTime > rNextTime ? 1 : -1);
    }
    static private AtomicReferenceFieldUpdater<RPCCall,DTask> CAS_DT =
      AtomicReferenceFieldUpdater.newUpdater(RPCCall.class, DTask.class,"_dt");
    boolean CAS_DT(DTask old, DTask nnn) { return CAS_DT.compareAndSet(this,old,nnn);  }

    // Assertion check that size is not changing between resends,
    // i.e., resends sent identical data.
    private boolean sz_check(AutoBuffer ab) {
      final int absize = ab.size();
      if( _size == 0 ) { _size = absize; return true; }
      return _size==absize;
    }
  }

  // Handle traffic, from a client to this server asking for work to be done.
  // Called from either a F/J thread (generally with a UDP packet) or from the
  // TCPReceiver thread.
  static void remote_exec( AutoBuffer ab ) {
    long lo = ab.get8(2), hi = ab.get8(10);
    final int task = ab.getTask();
    final int flag = ab.getFlag();
    assert flag==CLIENT_UDP_SEND || flag==CLIENT_TCP_SEND; // Client-side send
    // Atomically record an instance of this task, one-time-only replacing a
    // null with an RPCCall, a placeholder while we work on a proper response -
    // and it serves to let us discard dup UDP requests.
    RPCCall old = ab._h2o.has_task(task);
    // This is a UDP packet requesting an answer back for a request sent via
    // TCP but the UDP packet has arrived ahead of the TCP.  Just drop the UDP
    // and wait for the TCP to appear.
    if( old == null && flag == CLIENT_TCP_SEND ) {
      Log.warn("got tcp with existing task #, FROM " + ab._h2o.toString() + " AB: " /* +  UDP.printx16(lo,hi)*/);
      assert !ab.hasTCP():"ERROR: got tcp with existing task #, FROM " + ab._h2o.toString() + " AB: "  /* + UDP.printx16(lo,hi)*/;      // All the resends should be UDP only
      // DROP PACKET
    } else if( old == null ) {  // New task?
      RPCCall rpc;
      try {
        // Read the DTask Right Now.  If we are the TCPReceiver thread, then we
        // are reading in that thread... and thus TCP reads are single-threaded.
        rpc = new RPCCall(ab.get(water.DTask.class),ab._h2o,task);
      } catch( AutoBuffer.AutoBufferException e ) {
        // Here we assume it's a TCP fail on read - and ignore the remote_exec
        // request.  The caller will send it again.  NOTE: this case is
        // indistinguishable from a broken short-writer/long-reader bug, except
        // that we'll re-send endlessly and fail endlessly.
        Log.info("Network congestion OR short-writer/long-reader: TCP "+e._ioe.getMessage()+",  AB="+ab+", ignoring partial send");
        ab.drainClose();
        return;
      }
      RPCCall rpc2 = ab._h2o.record_task(rpc);
      if( rpc2==null ) {        // Atomically insert (to avoid double-work)
        if( rpc._dt instanceof MRTask && rpc._dt.logVerbose() )
          Log.debug("Start remote task#"+task+" "+rpc._dt.getClass()+" from "+ab._h2o);
        H2O.submitTask(rpc);    // And execute!
      } else {                  // Else lost the task-insertion race
        if(ab.hasTCP())  ab.drainClose();
        // DROP PACKET
      }

    } else if( !old._computedAndReplied) {
      // This packet has not been fully computed.  Hence it's still a work-in-
      // progress locally.  We have no answer to reply but we do not want to
      // re-offer the packet for repeated work.  Send back a NACK, letting the
      // client know we're Working On It
      if(ab.hasTCP()) {
        Log.warn("got tcp with existing task #, FROM " + ab._h2o.toString() + " AB: "  /* + UDP.printx16(lo,hi)*/ + ", position = " + ab._bb.position()); // All the resends should be UDP only
        ab.drainClose();
        ab = new AutoBuffer(ab._h2o);
      } else
        ab.clearForWriting().putTask(UDP.udp.nack.ordinal(), task);
      // DROP PACKET
    } else {
      // This is an old re-send of the same thing we've answered to before.
      // Send back the same old answer ACK.  If we sent via TCP before, then
      // we know the answer got there so just send a control-ACK back.  If we
      // sent via UDP, resend the whole answer.
      if(ab.hasTCP()) {
        Log.warn("got tcp with existing task #, FROM " + ab._h2o.toString() + " AB: " +  UDP.printx16(lo,hi)); // All the resends should be UDP only
        ab.drainClose();
      }
      ++old._ackResendCnt;
      if(old._ackResendCnt % 10 == 0) {
        Log.err("Possibly broken network, can not send ack through, got " + old._ackResendCnt + " resends. Restaring the small-tcp thread");
        old._client.restartSmallTCP();
      }
      old.resend_ack();
    }
    ab.close();
  }

  // TCP large RECEIVE of results.  Note that 'this' is NOT the RPC object
  // that is hoping to get the received object, nor is the current thread the
  // RPC thread blocking for the object.  The current thread is the TCP
  // reader thread.
  static void tcp_ack( final AutoBuffer ab ) throws IOException {
    // Get the RPC we're waiting on
    int task = ab.getTask();
    RPC rpc = ab._h2o.taskGet(task);
    // Race with canceling a large RPC fetch: Task is already dead.  Do not
    // bother reading from the TCP socket, just bail out & close socket.
    if( rpc == null || rpc._done) {
      ab.drainClose();
    } else {
      assert rpc._tasknum == task;
      assert !rpc._done;
      // Here we have the result, and we're on the correct Node but wrong
      // Thread.  If we just return, the TCP reader thread will close the
      // remote, the remote will UDP ACK the RPC back, and back on the current
      // Node but in the correct Thread, we'd wake up and realize we received a
      // large result.
      try {
        rpc.response(ab);
      } catch( AutoBuffer.AutoBufferException e ) {
        // If TCP fails, we will have done a short-read crushing the original
        // _dt object, and be unable to resend.  This is fatal right now.
        // Really: an unimplemented feature; fix is to notice that a partial
        // TCP read means that the server (1) got our remote_exec request, (2)
        // has computed an answer and was trying to send it to us, (3) failed
        // sending via TCP hence the server knows it failed and will send again
        // without any further work from us.  We need to disable all the resend
        // & retry logic, and wait for the server to re-send our result.
        // Meanwhile the _dt object is crushed with half-read crap, and cannot
        // be trusted except in the base fields.
        throw Log.throwErr(e._ioe);
      }
    }
    // ACKACK the remote, telling him "we got the answer"
    new AutoBuffer(ab._h2o).putTask(UDP.udp.ackack.ordinal(),task).close();
  }

  // Got a response UDP packet, or completed a large TCP answer-receive.
  // Install it as The Answer packet and wake up anybody waiting on an answer.
  // On all paths, send an ACKACK back
  static AutoBuffer ackack( AutoBuffer ab, int tnum ) {
    return ab.clearForWriting().putTask(UDP.udp.ackack.ordinal(),tnum);
  }
  protected AutoBuffer response( AutoBuffer ab ) {
    assert _tasknum==ab.getTask();
    if( _done ) {
      if(!ab.hasTCP())
        return ackack(ab, _tasknum); // Ignore duplicate response packet
      ab.drainClose();
    } else {
      int flag = ab.getFlag();       // Must read flag also, to advance ab
      if (flag == SERVER_TCP_SEND) return ackack(ab, _tasknum); // Ignore UDP packet for a TCP reply
      assert flag == SERVER_UDP_SEND:"flag = " + flag;
      synchronized (this) {             // Install the answer under lock
        if (_done) {
          if(!ab.hasTCP())
            return ackack(ab, _tasknum); // Ignore duplicate response packet
          ab.drainClose();
        } else {
          // don't do PENDING remove, too costly when having many threads
//          UDPTimeOutThread.PENDING.remove(this);
          _dt.read(ab);             // Read the answer (under lock?)
          _size_rez = ab.size();    // Record received size
          ab.close();               // Also finish the read (under lock?  even if canceled, since need to drain TCP)
          if (!isCancelled())      // Can be canceled already (locally by MRTask while recieving remote answer)
            _dt.onAck();            // One time only execute (before sending ACKACK)
          _done = true;             // Only read one (of many) response packets
          ab._h2o.taskRemove(_tasknum); // Flag as task-completed, even if the result is null
          notifyAll();              // And notify in any case
        }
        if (!isCancelled())  // Can be canceled already
          doAllCompletions(); // Send all tasks needing completion to the work queues
      }
    }
    // AckAck back on a fresh AutoBuffer, since actually closed() the incoming one
    return new AutoBuffer(ab._h2o).putTask(UDP.udp.ackack.ordinal(),_tasknum);
  }

  private void doAllCompletions() {
    final Exception e = _dt.getDException();
    // Also notify any and all pending completion-style tasks
    if( _fjtasks != null )
      for( final H2OCountedCompleter task : _fjtasks )
        H2O.submitTask(new H2OCountedCompleter() {
            @Override public void compute2() {
              if(e != null) // re-throw exception on this side as if it happened locally
                task.completeExceptionally(e);
              else try {
                  task.tryComplete();
                } catch(Throwable e) {
                  task.completeExceptionally(e);
                }
            }
            @Override public byte priority() { return task.priority(); }
          });
  }

  // ---
  public synchronized RPC<V> addCompleter( H2OCountedCompleter task ) {
    if( _fjtasks == null ) _fjtasks = new ArrayList(2);
    _fjtasks.add(task);
    return this;
  }

  // Assertion check that size is not changing between resends,
  // i.e., resends sent identical data.
  private boolean sz_check(AutoBuffer ab) {
    final int absize = ab.size();
    if( _size == 0 ) { _size = absize; return true; }
    return _size==absize;
  }
  // Size of received results
  int size_rez() { return _size_rez; }

  // ---
  static final long RETRY_MS = 200; // Initial UDP packet retry in msec
  // How long until we should do the "timeout" action?
  @Override public final long getDelay( TimeUnit unit ) {
    long delay = (_started+_retry)-System.currentTimeMillis();
    return unit.convert( delay, TimeUnit.MILLISECONDS );
  }
  // Needed for the DelayQueue API
  @Override public final int compareTo( Delayed t ) {
    RPC<?> dt = (RPC<?>)t;
    long nextTime = _started+_retry, dtNextTime = dt._started+dt._retry;
    return nextTime == dtNextTime ? 0 : (nextTime > dtNextTime ? 1 : -1);
  }
}
