package water.fvec

import water._

// Version of MRTask specialized for Array[Double]
class MRTask_AD[tmr <: MapReduce[Array[Double],tmr]](val mr : tmr) extends MRTask[MRTask_AD[tmr]] {
  var res : tmr = mr // junk assignment because scala demands initialization
  override def map( chks : Array[Chunk] ) : Unit = {
    val start = chks(0)._start
    val len = chks(0)._len
    if( len == 0 ) return
    // Specialize user map to double[]
    res = mr.clone
    val tmp : tmr = mr.clone // Something to reduce into
    // Temp buffer to hold data without reallocating each row
    val row = new Array[Double](chks.length)
    // For all rows in Chunk
    var i = 0
    // Find first available row & map it
    while( i < len && !fill(row,chks,i,mr.skipNA) ) i += 1
    res.map(row); i += 1
    // For all remaining rows, find available, map & reduce
    while( i < len ) {         // For all rows
      if( fill(row,chks,i,mr.skipNA) ) { // Fill all cols into 'row'
        tmp.map(row)          // Map into mr2
        res.reduce(tmp)       // Reduce mr2 into self
      }
      i += 1
    }
  }
  // Call user reduce
  override def reduce( mrt : MRTask_AD[tmr] ) = res.reduce(mrt.res)
  // Fill reused temp array from Chunks.  Returns false is any value is NaN & skipNA true
  private def fill(row : Array[Double], chks : Array[Chunk], i : Int, skipNA : Boolean) : Boolean = {
    if( skipNA ) {
      var col = 0
      while( col < chks.length ) {
        val d = chks(col).at0(i); row(col) = d
        if( d.isNaN ) return false
        col += 1
      }
    } else {
      var col = 0
      while( col < chks.length ) {
        row(col) = chks(col).at0(i)
        col += 1
      }
    }
    true
  }
}
