# FlexOrm

FlexOrm is a flexible and efficient ORM library for Java, enabling simple integration with MySQL, MongoDB, and SQLite. The library was designed with ease of use in mind while maintaining high flexibility and performance.

## Features

- **Multi-database support**: MySQL, MongoDB, SQLite
- **Fluent Query Builder**: Intuitive API for building queries
- **Entity relationships**: OneToOne, OneToMany, ManyToOne, ManyToMany
- **Lazy/Eager Loading**: Control over relationship loading
- **Cascade operations**: Automatic saving/deleting of related entities
- **Transactions**: Full transaction support
- **Migration system**: Database schema management
- **Entity validation**: Built-in field validation
- **Connection Pooling**: HikariCP for MySQL and SQLite

## Installation

### Maven

Add the Minecodes repository to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>minecodes</id>
    <url>https://maven.minecodes.pl/releases</url>
  </repository>
</repositories>
```

Then add the dependency:

```xml
<dependency>
  <groupId>pl.minecodes.orm</groupId>
  <artifactId>FlexOrm</artifactId>
  <version>2026.01.17.1</version>
</dependency>
```

### Gradle

Add the Minecodes repository to your `build.gradle`:

```groovy
repositories {
    maven { url 'https://maven.minecodes.pl/releases' }
}
```

Then add the dependency:

```groovy
implementation 'pl.minecodes.orm:FlexOrm:2026.01.17.1'
```

### Database dependencies

FlexOrm uses `provided` scope for database drivers - you need to add the driver for the database you want to use:

#### MySQL
```xml
<dependency>
  <groupId>mysql</groupId>
  <artifactId>mysql-connector-java</artifactId>
  <version>8.0.33</version>
</dependency>
```

#### MongoDB
```xml
<dependency>
  <groupId>org.mongodb</groupId>
  <artifactId>mongodb-driver-sync</artifactId>
  <version>5.2.1</version>
</dependency>
```

#### SQLite
```xml
<dependency>
  <groupId>org.xerial</groupId>
  <artifactId>sqlite-jdbc</artifactId>
  <version>3.46.0.0</version>
</dependency>
```

## Quick Start

### 1. Defining entities

```java
@OrmEntity(table = "users")
public class User {

  @OrmEntityId
  private Long id;

  @OrmField(length = 100)
  @OrmNotNull
  private String name;

  @OrmField(nullable = false)
  @OrmIndex(unique = true)
  private String email;

  @OrmOneToMany(targetEntity = Post.class, mappedBy = "author", fetch = FetchType.LAZY)
  private List<Post> posts;

  // No-argument constructor required
  public User() {}

  // Getters and setters...
}
```

### 2. Database connection

```java
// MySQL
FlexOrm orm = FlexOrm.mysql("localhost", 3306, "database", "user", "password").connect();

// SQLite
FlexOrm orm = FlexOrm.sqllite("database.db").connect();

// MongoDB
FlexOrm orm = FlexOrm.mongodb("localhost", 27017, "database", "user", "password", new Gson()).connect();
```

### 3. CRUD operations

```java
EntityRepository<User, Long> userRepository = orm.getEntityRepository(User.class);

// Create
User user = new User();
user.setName("John Smith");
user.setEmail("john@example.com");
userRepository.save(user);

// Read
Optional<User> found = userRepository.findById(1L);
List<User> all = userRepository.findAll();

// Update
user.setName("John Doe");
userRepository.update(user);

// Delete
userRepository.delete(user);
userRepository.deleteById(1L);
```

## Query Builder

FlexOrm provides a fluent API for building queries:

```java
List<User> users = userRepository.query()
    .where("name", Operator.LIKE, "John%")
    .and("email", Operator.IS_NOT_NULL, null)
    .orderBy("name", true)
    .limit(10)
    .offset(0)
    .execute();

long count = userRepository.query()
    .where("active", Operator.EQUALS, true)
    .count();
```

### Available operators

- `EQUALS` - equality
- `NOT_EQUALS` - inequality
- `GREATER_THAN` - greater than
- `LESS_THAN` - less than
- `GREATER_THAN_OR_EQUALS` - greater than or equal
- `LESS_THAN_OR_EQUALS` - less than or equal
- `LIKE` - pattern matching
- `IN` - value in set
- `IS_NULL` - is null
- `IS_NOT_NULL` - is not null

## Relationships

### OneToOne

```java
@OrmEntity(table = "users")
public class User {
  @OrmEntityId
  private Long id;

  @OrmOneToOne(targetEntity = Profile.class, joinColumn = "profile_id", fetch = FetchType.EAGER)
  private Profile profile;
}
```

### OneToMany / ManyToOne

```java
@OrmEntity(table = "posts")
public class Post {
  @OrmEntityId
  private Long id;

  @OrmManyToOne(targetEntity = User.class, joinColumn = "author_id")
  private User author;
}

@OrmEntity(table = "users")
public class User {
  @OrmOneToMany(targetEntity = Post.class, mappedBy = "author", cascade = true)
  private List<Post> posts;
}
```

### ManyToMany

```java
@OrmEntity(table = "users")
public class User {
  @OrmManyToMany(
    targetEntity = Role.class,
    joinTable = "user_roles",
    joinColumn = "user_id",
    inverseJoinColumn = "role_id"
  )
  private Set<Role> roles;
}
```

## Transactions

```java
userRepository.beginTransaction();
try {
  userRepository.save(user1);
  userRepository.save(user2);
  userRepository.commitTransaction();
} catch (Exception e) {
  userRepository.rollbackTransaction();
  throw e;
}
```

## Schema Management

### Creating tables

```java
TableManager tableManager = new TableManager(orm);
tableManager.createOrUpdateTable(User.class);
```

### Migrations

```java
MigrationManager migrationManager = new MigrationManager(orm);

migrationManager.addMigration(new Migration() {
  @Override public int getVersion() { return 1; }
  @Override public String getDescription() { return "Create users table"; }
  @Override public String getUpSql() { return "CREATE TABLE users (...)"; }
  @Override public String getDownSql() { return "DROP TABLE users"; }
});

migrationManager.migrate();  // Apply migrations
migrationManager.rollback(); // Rollback last migration
```

## Annotations

| Annotation | Description |
|------------|-------------|
| `@OrmEntity` | Marks class as an entity |
| `@OrmEntityId` | Marks field as primary key |
| `@OrmField` | Column configuration (name, nullable, length, defaultValue) |
| `@OrmTransient` | Field excluded from persistence |
| `@OrmIndex` | Creates index on column |
| `@OrmNotNull` | Validation - field cannot be null |
| `@OrmOneToOne` | One-to-one relationship |
| `@OrmOneToMany` | One-to-many relationship |
| `@OrmManyToOne` | Many-to-one relationship |
| `@OrmManyToMany` | Many-to-many relationship |

## Supported Types

- Primitives: `String`, `int`, `long`, `double`, `float`, `boolean`
- Wrappers: `Integer`, `Long`, `Double`, `Float`, `Boolean`
- Date/Time: `Date`, `Timestamp`, `LocalDateTime`, `LocalDate`, `LocalTime`
- Numeric: `BigDecimal`, `BigInteger`
- Other: `byte[]`, `Enum`

## Exceptions

- `OrmException` - base ORM exception
- `ValidationException` - entity validation error
- `ConnectionException` - connection error
- `QueryException` - query execution error
- `EntityNotFoundException` - entity not found
- `TransactionException` - transaction error

## License

MIT License
