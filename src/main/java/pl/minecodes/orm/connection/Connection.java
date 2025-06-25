package pl.minecodes.orm.connection;

public interface DatabaseConnection<T> {

  void connect();

  T getConnection();

}