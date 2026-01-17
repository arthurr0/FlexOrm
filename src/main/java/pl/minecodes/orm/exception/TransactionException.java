package pl.minecodes.orm.exception;

public class TransactionException extends OrmException {
  public TransactionException(String message) {
    super(message);
  }

  public TransactionException(String message, Throwable cause) {
    super(message, cause);
  }
}
