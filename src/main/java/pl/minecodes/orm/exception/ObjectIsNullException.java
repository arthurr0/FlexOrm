package pl.minecodes.orm.exception;

public class ObjectIsNullException extends RuntimeException {
  public ObjectIsNullException(String message) {
    super(message);
  }
}
