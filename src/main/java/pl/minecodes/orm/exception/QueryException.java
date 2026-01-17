package pl.minecodes.orm.exception;

public class QueryException extends OrmException {

  public QueryException(String message) {
    super(message);
  }

  public QueryException(String message, Throwable cause) {
    super(message, cause);
  }
}
