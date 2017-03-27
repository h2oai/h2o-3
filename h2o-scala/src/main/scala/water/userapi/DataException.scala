package water.userapi

/**
  * Wrapper for all kinds of H2O and IO exceptions
  * 
  * Created by vpatryshev on 3/26/17.
  */
case class DataException(val message: String, 
                         val cause: Option[Exception] = None) 
  extends RuntimeException {
  
  def this(cause: Exception) = this(cause.getMessage, Some(cause))
}
