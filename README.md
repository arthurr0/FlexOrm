# FlexOrm
# FlexOrm

FlexOrm to elastyczna i wydajna biblioteka ORM dla języka Java, umożliwiająca prostą integrację z różnymi typami baz danych, w tym MySQL, MongoDB, SQLite oraz JSON. Biblioteka zaprojektowana została z myślą o łatwości użycia przy zachowaniu dużej elastyczności i wydajności.

## Spis treści

1. [Instalacja](#instalacja)
2. [Szybki start](#szybki-start)
3. [Konfiguracja połączeń z bazami danych](#konfiguracja-połączeń-z-bazami-danych)
4. [Definiowanie encji](#definiowanie-encji)
5. [EntityAgent - zarządzanie danymi](#entityagent---zarządzanie-danymi)
6. [Operacje CRUD](#operacje-crud)
7. [Wykonywanie zapytań](#wykonywanie-zapytań)
8. [Transakcje](#transakcje)
9. [Zaawansowane funkcje](#zaawansowane-funkcje)
10. [FAQ](#faq)

## Instalacja

Aby dodać FlexOrm do swojego projektu, dodaj następującą zależność do pliku `pom.xml`:
Easy way to support few databases in one project.

## Usage

##### Dependencies required per DatabaseType
Since you won't always be supporting all of these types, these dependencies are not stored in the FlexOrm API.
When you use any of these database types, add these dependencies to your project.

#### Json
```xml
    <dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.13.0</version>
    </dependency>
```

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

#### SqlLite
```xml
    <dependency>
      <groupId>org.xerial</groupId>
      <artifactId>sqlite-jdbc</artifactId>
      <version>3.46.0.0</version>
    </dependency>
```