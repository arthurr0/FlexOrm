package pl.minecodes.orm.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.entity.EntityRepository;

class QueryBuilderTest {

  @TempDir
  Path tempDir;

  private FlexOrm flexOrm;
  private EntityRepository<QueryTestEntity, Long> repository;

  @BeforeEach
  void setUp() {
    File dbFile = tempDir.resolve("query-test.db").toFile();
    flexOrm = FlexOrm.sqllite(dbFile);
    flexOrm.connect();
    repository = flexOrm.getEntityRepository(QueryTestEntity.class);
    repository.executeUpdate(
        "CREATE TABLE IF NOT EXISTS query_test (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, age INTEGER, score REAL, active INTEGER)");

    repository.save(new QueryTestEntity("Alice", 25, 85.5, true));
    repository.save(new QueryTestEntity("Bob", 30, 90.0, true));
    repository.save(new QueryTestEntity("Charlie", 35, 75.5, false));
    repository.save(new QueryTestEntity("Diana", 28, 95.0, true));
    repository.save(new QueryTestEntity("Eve", 22, 88.5, false));
  }

  @Test
  void testWhereEquals() {
    List<QueryTestEntity> result = repository.query()
        .where("name", Operator.EQUALS, "Alice")
        .execute();

    assertEquals(1, result.size());
    assertEquals("Alice", result.get(0).getName());
  }

  @Test
  void testWhereNotEquals() {
    List<QueryTestEntity> result = repository.query()
        .where("name", Operator.NOT_EQUALS, "Alice")
        .execute();

    assertEquals(4, result.size());
    assertTrue(result.stream().noneMatch(e -> e.getName().equals("Alice")));
  }

  @Test
  void testWhereGreaterThan() {
    List<QueryTestEntity> result = repository.query()
        .where("age", Operator.GREATER_THAN, 28)
        .execute();

    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(e -> e.getAge() > 28));
  }

  @Test
  void testWhereLessThan() {
    List<QueryTestEntity> result = repository.query()
        .where("age", Operator.LESS_THAN, 28)
        .execute();

    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(e -> e.getAge() < 28));
  }

  @Test
  void testWhereGreaterThanOrEquals() {
    List<QueryTestEntity> result = repository.query()
        .where("age", Operator.GREATER_THAN_OR_EQUALS, 30)
        .execute();

    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(e -> e.getAge() >= 30));
  }

  @Test
  void testWhereLessThanOrEquals() {
    List<QueryTestEntity> result = repository.query()
        .where("age", Operator.LESS_THAN_OR_EQUALS, 25)
        .execute();

    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(e -> e.getAge() <= 25));
  }

  @Test
  void testWhereLike() {
    List<QueryTestEntity> result = repository.query()
        .where("name", Operator.LIKE, "A%")
        .execute();

    assertEquals(1, result.size());
    assertEquals("Alice", result.get(0).getName());
  }

  @Test
  void testWhereLikeMiddle() {
    List<QueryTestEntity> result = repository.query()
        .where("name", Operator.LIKE, "%li%")
        .execute();

    assertEquals(2, result.size());
  }

  @Test
  void testWhereIn() {
    List<QueryTestEntity> result = repository.query()
        .where("name", Operator.IN, Arrays.asList("Alice", "Bob", "Charlie"))
        .execute();

    assertEquals(3, result.size());
  }

  @Test
  void testWhereInEmpty() {
    List<QueryTestEntity> result = repository.query()
        .where("name", Operator.IN, List.of())
        .execute();

    assertEquals(0, result.size());
  }

  @Test
  void testAndCondition() {
    List<QueryTestEntity> result = repository.query()
        .where("age", Operator.GREATER_THAN, 25)
        .and("active", Operator.EQUALS, 1)
        .execute();

    assertEquals(2, result.size());
  }

  @Test
  void testOrCondition() {
    List<QueryTestEntity> result = repository.query()
        .where("name", Operator.EQUALS, "Alice")
        .or("name", Operator.EQUALS, "Bob")
        .execute();

    assertEquals(2, result.size());
  }

  @Test
  void testOrderByAscending() {
    List<QueryTestEntity> result = repository.query()
        .orderBy("age", true)
        .limit(5)
        .execute();

    assertEquals(5, result.size());
    int minAge = result.stream().mapToInt(QueryTestEntity::getAge).min().orElse(0);
    int maxAge = result.stream().mapToInt(QueryTestEntity::getAge).max().orElse(0);
    assertEquals(22, minAge);
    assertEquals(35, maxAge);
  }

  @Test
  void testOrderByDescending() {
    List<QueryTestEntity> result = repository.query()
        .orderBy("age", false)
        .limit(5)
        .execute();

    assertEquals(5, result.size());
    int minAge = result.stream().mapToInt(QueryTestEntity::getAge).min().orElse(0);
    int maxAge = result.stream().mapToInt(QueryTestEntity::getAge).max().orElse(0);
    assertEquals(22, minAge);
    assertEquals(35, maxAge);
  }

  @Test
  void testLimit() {
    List<QueryTestEntity> result = repository.query()
        .orderBy("age")
        .limit(2)
        .execute();

    assertEquals(2, result.size());
  }

  @Test
  void testOffset() {
    List<QueryTestEntity> result = repository.query()
        .orderBy("age")
        .limit(100)
        .offset(2)
        .execute();

    assertEquals(3, result.size());
  }

  @Test
  void testLimitAndOffset() {
    List<QueryTestEntity> result = repository.query()
        .orderBy("age")
        .limit(2)
        .offset(1)
        .execute();

    assertEquals(2, result.size());
  }

  @Test
  void testCount() {
    long count = repository.query()
        .where("active", Operator.EQUALS, 1)
        .count();

    assertEquals(3, count);
  }

  @Test
  void testCountAll() {
    long count = repository.query().count();

    assertEquals(5, count);
  }

  @Test
  void testDistinct() {
    repository.save(new QueryTestEntity("Alice", 26, 80.0, true));

    List<QueryTestEntity> result = repository.query()
        .distinct()
        .execute();

    assertEquals(6, result.size());
  }

  @Test
  void testComplexQuery() {
    List<QueryTestEntity> result = repository.query()
        .where("age", Operator.GREATER_THAN_OR_EQUALS, 25)
        .and("score", Operator.GREATER_THAN, 80.0)
        .orderBy("score", false)
        .limit(3)
        .execute();

    assertTrue(result.size() <= 3);
    assertTrue(result.size() > 0);
    for (QueryTestEntity e : result) {
      assertTrue(e.getAge() >= 25);
      assertTrue(e.getScore() > 80.0);
    }
  }

  @Test
  void testWhereWithDefaultOperator() {
    List<QueryTestEntity> result = repository.query()
        .where("name", "Alice")
        .execute();

    assertEquals(1, result.size());
    assertEquals("Alice", result.get(0).getName());
  }

  @Test
  void testMultipleAndConditions() {
    List<QueryTestEntity> result = repository.query()
        .where("age", Operator.GREATER_THAN, 20)
        .and("age", Operator.LESS_THAN, 30)
        .and("active", Operator.EQUALS, 1)
        .execute();

    assertEquals(2, result.size());
  }

  @Test
  void testScoreComparison() {
    List<QueryTestEntity> result = repository.query()
        .where("score", Operator.GREATER_THAN_OR_EQUALS, 90.0)
        .execute();

    assertEquals(2, result.size());
  }

  @OrmEntity(table = "query_test")
  public static class QueryTestEntity {

    @OrmEntityId
    private Long id;

    @OrmField
    private String name;

    @OrmField
    private int age;

    @OrmField
    private double score;

    @OrmField
    private boolean active;

    public QueryTestEntity() {
    }

    public QueryTestEntity(String name, int age, double score, boolean active) {
      this.name = name;
      this.age = age;
      this.score = score;
      this.active = active;
    }

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

    public int getAge() {
      return age;
    }

    public void setAge(int age) {
      this.age = age;
    }

    public double getScore() {
      return score;
    }

    public void setScore(double score) {
      this.score = score;
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }
  }
}
