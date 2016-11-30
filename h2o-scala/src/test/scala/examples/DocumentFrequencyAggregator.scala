package examples

class DocumentFrequencyAggregator(size: Int) extends Serializable {

  /** number of documents */
  private var m = 0L
  /** document frequency vector */
  private var df: Array[Long] = new Array[Long](size)

  def this() = this(0)

  /** Adds a new frequency vector. */
  def +(doc: Array[Double]): this.type = {
    for { i <- doc.indices } if (doc(i) > 0) df(i) += 1

    m += 1L
    this
  }

  /** Merges another. */
  def merge(other: DocumentFrequencyAggregator): this.type = {
    if (!other.isEmpty) {
      if (isEmpty) {
        df = new Array[Long](other.df.length)
        Array.copy(other.df, 0, df, 0, length = other.df.length)
      } else {
        df.indices foreach (i => df(i) += other.df(i))
      }
      m += other.m
    }
    this
  }

  private def isEmpty: Boolean = m == 0L

  /** Returns the current IDF vector. */
  def idf(minDocFreq: Int): Array[Double] = {
    if (isEmpty) {
      throw new IllegalStateException("Haven't seen any document yet.")
    }
    val inv = df map (x => if (x >= minDocFreq) math.log((m + 1.0) / (x + 1.0)) else 0)

    inv
  }
}
