package pl.minecodes.orm.migration;

public interface Migration {

  int version();

  String description();

  String upSql();

  String downSql();
}
