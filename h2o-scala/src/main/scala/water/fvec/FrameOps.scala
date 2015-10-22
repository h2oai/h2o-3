package water.fvec

import water.Futures

import scala.util.{Failure, Success, Try}

/**
 * High-level DSL proving user-friendly operations
 * on top of H2O Frame.
 */
trait FrameOps { self: H2OFrame =>  // Mix only with H2OFrame types

  /** Functional type to transform vectors. */
  type VecTransformation = ((String, Vec) => Vec)

  /** Functional type to select vectors. */
  type VecSelector = ((String, Vec) => Boolean)

  /** Create a sub-frame based on the list of column names.
    *
    * @param columnNames  name of columns which will compose a new frame
    * @return  a new H2O Frame composed of selected vectors
    */
  def apply(columnNames: Array[String]): H2OFrame = new H2OFrame(subframe(columnNames))

  /** Create a sub-frame based on the list of column names.
    *
    * @param columnNames  name of columns which will compose a new frame
    * @return  a new H2O Frame composed of selected vectors
    */
  def apply(columnNames: Symbol*): H2OFrame = apply(columnNames.map(_.name).toArray)

  def apply(transformation: VecTransformation, colNames: Symbol*): H2OFrame =
    apply(transformation, colNames.map(_.name).toArray)

  def apply(transformation: VecTransformation, colNames: Array[String]): H2OFrame = {
    apply(transformation, (name, vec) => colNames.contains(name))
  }

  def apply(transformation: VecTransformation, selector: VecSelector, removeVec: Boolean = true): H2OFrame = {
    val vecs = self.vecs()
    val names = self.names()
    val futures = new Futures()
    vecs.indices.filter(idx => {
      val name = names(idx)
      val vec  = vecs(idx)
      // Get only indexes of selected vectors
      selector(name, vec)
    }).map(idx => {
      val name = names(idx)
      val vec = vecs(idx)
      (idx, transformation(name, vec))
    }).foreach { case (idx: Int, newVec: Vec) => {
      val v = self.replace(idx, newVec)
      if (removeVec) v.remove(futures)
    }}
    // Block for all fired deletes
    futures.blockForPending()
    self
  }

  /**
    * Transform columns in enum columns
    * @param cols : Array[ String ] containing all the names of enum columns
    */
  def colToEnum(cols: Array[String]): Unit = {
    if(!cols.map(name => { if (!this.names.contains(name)) false}).contains(false)) {
      val indexes = this.find(cols)
      indexes.zipWithIndex.map(i => this.replace(this.find(cols(i._2)),this.vec(i._1).toCategoricalVec))
      this.update(null)
    } else {
      throw new IllegalArgumentException("One or several columns are not present in your DataFrame")
    }
  }

  /**
    * Transform columns in enum columns
    * @param cols : Array[ Int ] containing all the indexes of enum columns
    */
  def colToEnum(cols: Array[Int]): Unit = {
    Try(cols.map(i => this.name(i))) match {
      case Success(s) => colToEnum(s)
      case Failure(t) => println(t)
    }
  }

  /**
    * Rename a column of your DataFrame
    * @param index : Index of the column to rename
    * @param newName : New name
    */
  def rename(index: Int, newName: String): Unit ={
    val tmp = this.names
    Try(tmp(index) = newName) match {
      case Success(_) => this._names = tmp
      case Failure(t) => println(t)
    }
  }

  /**
    * Rename a column of your DataFrame
    * @param oldName : Old name
    * @param newName : New name
    */
  def rename(oldName: String, newName: String): Unit ={
    val index = this.find(oldName)
    if(index != -1){
      rename(index, newName)
    }else{
      throw new IllegalArgumentException("Column missing")
    }
  }
}
