package pl.minecodes.orm.migration;

public interface Migration {
  int getVersion();
  String getDescription();
  String getUpSql();
  String getDownSql();
}
