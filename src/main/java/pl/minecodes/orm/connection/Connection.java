package pl.minecodes.orm.connection;

public interface Connection<T> {

  void connect();

  T getConnection();

}