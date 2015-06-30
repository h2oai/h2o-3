package water.fvec

import scala.util.{Failure, Success, Try}

/**
 * High-level DSL proving user-friendly operations
 * on top of H2O Frame.
 */
trait FrameOps { self: Frame =>

  /**
    * Transform columns in enum columns
    * @param cols : Array[ String ] containing all the names of enum columns
    */
  def colToEnum(cols: Array[String]): Unit = {
    if(!cols.map(name => { if (!this.names.contains(name)) false}).contains(false)) {
      val indexes = this.find(cols)
      indexes.zipWithIndex.map(i => this.replace(this.find(cols(i._2)),this.vec(i._1).toEnum))
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
