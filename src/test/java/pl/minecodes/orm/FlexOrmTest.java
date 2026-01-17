package pl.minecodes.orm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.entity.EntityRepository;

class FlexOrmTest {

  @TempDir
  Path tempDir;

  @Test
  void testSqliteFactory() {
    File dbFile = tempDir.resolve("test.db").toFile();
    FlexOrm orm = FlexOrm.sqllite(dbFile);

    assertNotNull(orm);
    assertEquals(DatabaseType.SQLLITE, orm.getDatabaseType());
  }

  @Test
  void testConnect() {
    File dbFile = tempDir.resolve("test.db").toFile();
    FlexOrm orm = FlexOrm.sqllite(dbFile);

    assertDoesNotThrow(() -> orm.connect());
    assertNotNull(orm.getConnection());
  }

  @Test
  void testGetEntityRepository() {
    File dbFile = tempDir.resolve("test.db").toFile();
    FlexOrm orm = FlexOrm.sqllite(dbFile);
    orm.connect();

    EntityRepository<TestFlexEntity, Long> agent = orm.getEntityRepository(TestFlexEntity.class);

    assertNotNull(agent);
  }

  @Test
  void testGetSameEntityRepositoryTwice() {
    File dbFile = tempDir.resolve("test.db").toFile();
    FlexOrm orm = FlexOrm.sqllite(dbFile);
    orm.connect();

    EntityRepository<TestFlexEntity, Long> agent1 = orm.getEntityRepository(TestFlexEntity.class);
    EntityRepository<TestFlexEntity, Long> agent2 = orm.getEntityRepository(TestFlexEntity.class);

    assertNotNull(agent1);
    assertNotNull(agent2);
  }

  @Test
  void testDatabaseTypeImmutable() {
    File dbFile = tempDir.resolve("test.db").toFile();
    FlexOrm orm = FlexOrm.sqllite(dbFile);

    DatabaseType type1 = orm.getDatabaseType();
    orm.connect();
    DatabaseType type2 = orm.getDatabaseType();

    assertEquals(type1, type2);
    assertEquals(DatabaseType.SQLLITE, type1);
  }

  @Test
  void testMultipleOrmInstances() {
    File dbFile1 = tempDir.resolve("test1.db").toFile();
    File dbFile2 = tempDir.resolve("test2.db").toFile();

    FlexOrm orm1 = FlexOrm.sqllite(dbFile1);
    FlexOrm orm2 = FlexOrm.sqllite(dbFile2);

    orm1.connect();
    orm2.connect();

    assertNotNull(orm1.getConnection());
    assertNotNull(orm2.getConnection());
    assertNotSame(orm1.getConnection(), orm2.getConnection());
  }

  @Test
  void testSqliteWithCustomPath() {
    File customDir = tempDir.resolve("custom/path").toFile();
    customDir.mkdirs();
    File dbFile = new File(customDir, "custom.db");

    FlexOrm orm = FlexOrm.sqllite(dbFile);
    orm.connect();

    assertNotNull(orm.getConnection());
  }

  @Test
  void testSqliteCreatesFile() {
    File dbFile = tempDir.resolve("should-create.db").toFile();
    assertFalse(dbFile.exists());

    FlexOrm orm = FlexOrm.sqllite(dbFile);
    orm.connect();

    EntityRepository<TestFlexEntity, Long> agent = orm.getEntityRepository(TestFlexEntity.class);
    agent.executeUpdate("CREATE TABLE IF NOT EXISTS test_flex (id INTEGER PRIMARY KEY, name TEXT)");

    assertTrue(dbFile.exists());
  }

  @OrmEntity(table = "test_flex")
  public static class TestFlexEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private String name;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
