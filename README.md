# FlexOrm

FlexOrm to elastyczna i wydajna biblioteka ORM dla języka Java, umożliwiająca prostą integrację z MySQL, MongoDB i SQLite. Biblioteka zaprojektowana została z myślą o łatwości użycia przy zachowaniu dużej elastyczności i wydajności.

## Funkcje

- **Wsparcie dla wielu baz danych**: MySQL, MongoDB, SQLite
- **Fluent Query Builder**: Intuicyjne API do budowania zapytań
- **Relacje między encjami**: OneToOne, OneToMany, ManyToOne, ManyToMany
- **Lazy/Eager Loading**: Kontrola nad ładowaniem relacji
- **Operacje kaskadowe**: Automatyczne zapisywanie/usuwanie powiązanych encji
- **Transakcje**: Pełne wsparcie dla transakcji
- **System migracji**: Zarządzanie schematem bazy danych
- **Walidacja encji**: Wbudowana walidacja pól
- **Connection Pooling**: HikariCP dla MySQL i SQLite

## Instalacja

### Maven

```xml
<dependency>
  <groupId>pl.minecodes.orm</groupId>
  <artifactId>FlexOrm</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Zależności dla baz danych

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

## Szybki start

### 1. Definiowanie encji

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

  // Konstruktor bezargumentowy wymagany
  public User() {}

  // Gettery i settery...
}
```

### 2. Połączenie z bazą danych

```java
// MySQL
FlexOrm orm = FlexOrm.mysql("localhost", 3306, "database", "user", "password").connect();

// SQLite
FlexOrm orm = FlexOrm.sqllite("database.db").connect();

// MongoDB
FlexOrm orm = FlexOrm.mongodb("localhost", 27017, "database", "user", "password", new Gson()).connect();
```

### 3. Operacje CRUD

```java
EntityAgent<User, Long> userAgent = orm.getEntityAgent(User.class);

// Create
User user = new User();
user.setName("Jan Kowalski");
user.setEmail("jan@example.com");
userAgent.save(user);

// Read
Optional<User> found = userAgent.findById(1L);
List<User> all = userAgent.findAll();

// Update
user.setName("Jan Nowak");
userAgent.update(user);

// Delete
userAgent.delete(user);
userAgent.deleteById(1L);
```

## Query Builder

FlexOrm oferuje fluent API do budowania zapytań:

```java
List<User> users = userAgent.query()
    .where("name", Operator.LIKE, "Jan%")
    .and("email", Operator.IS_NOT_NULL, null)
    .orderBy("name", true)
    .limit(10)
    .offset(0)
    .execute();

long count = userAgent.query()
    .where("active", Operator.EQUALS, true)
    .count();
```

### Dostępne operatory

- `EQUALS` - równość
- `NOT_EQUALS` - nierówność
- `GREATER_THAN` - większy niż
- `LESS_THAN` - mniejszy niż
- `GREATER_THAN_OR_EQUALS` - większy lub równy
- `LESS_THAN_OR_EQUALS` - mniejszy lub równy
- `LIKE` - dopasowanie wzorca
- `IN` - wartość w zbiorze
- `IS_NULL` - jest null
- `IS_NOT_NULL` - nie jest null

## Relacje

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

## Transakcje

```java
userAgent.beginTransaction();
try {
  userAgent.save(user1);
  userAgent.save(user2);
  userAgent.commitTransaction();
} catch (Exception e) {
  userAgent.rollbackTransaction();
  throw e;
}
```

## Zarządzanie schematem

### Tworzenie tabel

```java
TableManager tableManager = new TableManager(orm);
tableManager.createOrUpdateTable(User.class);
```

### Migracje

```java
MigrationManager migrationManager = new MigrationManager(orm);

migrationManager.addMigration(new Migration() {
  @Override public int getVersion() { return 1; }
  @Override public String getDescription() { return "Create users table"; }
  @Override public String getUpSql() { return "CREATE TABLE users (...)"; }
  @Override public String getDownSql() { return "DROP TABLE users"; }
});

migrationManager.migrate();  // Zastosuj migracje
migrationManager.rollback(); // Cofnij ostatnią migrację
```

## Adnotacje

| Adnotacja | Opis |
|-----------|------|
| `@OrmEntity` | Oznacza klasę jako encję |
| `@OrmEntityId` | Oznacza pole jako klucz główny |
| `@OrmField` | Konfiguracja kolumny (name, nullable, length, defaultValue) |
| `@OrmTransient` | Pole pomijane przy persystencji |
| `@OrmIndex` | Tworzy indeks na kolumnie |
| `@OrmNotNull` | Walidacja - pole nie może być null |
| `@OrmOneToOne` | Relacja jeden-do-jednego |
| `@OrmOneToMany` | Relacja jeden-do-wielu |
| `@OrmManyToOne` | Relacja wiele-do-jednego |
| `@OrmManyToMany` | Relacja wiele-do-wielu |

## Obsługiwane typy

- Podstawowe: `String`, `int`, `long`, `double`, `float`, `boolean`
- Wrapper: `Integer`, `Long`, `Double`, `Float`, `Boolean`
- Data/Czas: `Date`, `Timestamp`, `LocalDateTime`, `LocalDate`, `LocalTime`
- Numeryczne: `BigDecimal`, `BigInteger`
- Inne: `byte[]`, `Enum`

## Wyjątki

- `OrmException` - bazowy wyjątek ORM
- `ValidationException` - błąd walidacji encji
- `ConnectionException` - błąd połączenia
- `QueryException` - błąd wykonania zapytania
- `EntityNotFoundException` - encja nie znaleziona
- `TransactionException` - błąd transakcji

## Licencja

MIT License
