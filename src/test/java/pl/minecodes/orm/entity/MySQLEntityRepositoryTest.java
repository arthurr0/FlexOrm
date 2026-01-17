package pl.minecodes.orm.entity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.minecodes.orm.FlexOrm;

import java.util.List;
import java.util.Optional;
import pl.minecodes.orm.query.Operator;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class MySQLEntityRepositoryTest {

    @Container
    private static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    private FlexOrm flexOrm;
    private EntityRepository<TestEntity, Long> repository;

    @BeforeEach
    void setUp() {
        flexOrm = FlexOrm.mysql(
                mysqlContainer.getHost(),
                mysqlContainer.getFirstMappedPort(),
                mysqlContainer.getDatabaseName(),
                mysqlContainer.getUsername(),
                mysqlContainer.getPassword()
        );
        flexOrm.connect();
        repository = flexOrm.getEntityRepository(TestEntity.class);

        // Create table for TestEntity
        repository.executeUpdate("CREATE TABLE IF NOT EXISTS testentity (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), age INT, active BOOLEAN)");

    }

    @AfterEach
    void tearDown() {
        repository.executeUpdate("DROP TABLE IF EXISTS testentity");
    }

    @Test
    void testSaveAndFindById() {
        TestEntity entity = new TestEntity("Test Name", 25, true);
        repository.save(entity);

        assertNotNull(entity.getId(), "ID should be set after saving");

        Optional<TestEntity> foundEntity = repository.findById(entity.getId());
        assertTrue(foundEntity.isPresent(), "Entity should be found by ID");
        assertEquals(entity.getName(), foundEntity.get().getName());
        assertEquals(entity.getAge(), foundEntity.get().getAge());
        assertEquals(entity.isActive(), foundEntity.get().isActive());
    }

    @Test
    void testUpdate() {
        TestEntity entity = new TestEntity("Initial Name", 30, true);
        repository.save(entity);
        Long id = entity.getId();

        entity.setName("Updated Name");
        entity.setAge(31);
        entity.setActive(false);
        repository.update(entity);

        Optional<TestEntity> foundEntity = repository.findById(id);
        assertTrue(foundEntity.isPresent());
        assertEquals("Updated Name", foundEntity.get().getName());
        assertEquals(31, foundEntity.get().getAge());
        assertFalse(foundEntity.get().isActive());
    }

    @Test
    void testDelete() {
        TestEntity entity = new TestEntity("To Delete", 40, true);
        repository.save(entity);
        Long id = entity.getId();

        assertTrue(repository.findById(id).isPresent());

        repository.delete(entity);

        assertFalse(repository.findById(id).isPresent());
    }

    @Test
    void testDeleteById() {
        TestEntity entity = new TestEntity("To Delete By ID", 41, false);
        repository.save(entity);
        Long id = entity.getId();

        assertTrue(repository.findById(id).isPresent());

        repository.deleteById(id);

        assertFalse(repository.findById(id).isPresent());
    }

    @Test
    void testFindAll() {
        repository.save(new TestEntity("Entity 1", 25, true));
        repository.save(new TestEntity("Entity 2", 30, false));
        repository.save(new TestEntity("Entity 3", 35, true));

        List<TestEntity> entities = repository.findAll();

        assertEquals(3, entities.size());
    }

    @Test
    void testFindByField() {
        repository.save(new TestEntity("Active Entity 1", 25, true));
        repository.save(new TestEntity("Inactive Entity", 30, false));
        repository.save(new TestEntity("Active Entity 2", 35, true));

        List<TestEntity> activeEntities = repository.findByField("active", true);

        assertEquals(2, activeEntities.size());
        for (TestEntity entity : activeEntities) {
            assertTrue(entity.isActive());
        }
    }

    @Test
    void testExecuteRawQuery() {
        repository.save(new TestEntity("Raw Query Test", 50, true));

        List<TestEntity> results = repository.executeQuery("SELECT * FROM testentity WHERE age = 50");

        assertEquals(1, results.size());
        assertEquals("Raw Query Test", results.get(0).getName());
    }

    @Test
    void testTransaction() {
        repository.beginTransaction();

        try {
            repository.save(new TestEntity("Transaction Entity 1", 60, true));
            repository.save(new TestEntity("Transaction Entity 2", 61, false));
            repository.commitTransaction();
        } catch (Exception e) {
            repository.rollbackTransaction();
        }

        List<TestEntity> entities = repository.findAll();
        assertEquals(2, entities.size());
    }

    @Test
    void testTransactionRollback() {
        repository.beginTransaction();

        try {
            repository.save(new TestEntity("Transaction Entity 1", 70, true));
            repository.save(new TestEntity("Transaction Entity 2", 71, false));
            repository.rollbackTransaction();
        } catch (Exception e) {
            repository.rollbackTransaction();
        }

        List<TestEntity> entities = repository.findAll();
        assertEquals(0, entities.size());
    }

    @Test
    void testQueryBuilder() {
        repository.save(new TestEntity("Query Builder 1", 80, true));
        repository.save(new TestEntity("Query Builder 2", 81, false));
        repository.save(new TestEntity("Query Builder 3", 82, true));

        List<TestEntity> result = repository.query()
                .where("age", Operator.GREATER_THAN, 80)
                .execute();

        assertEquals(2, result.size());
    }

    @Test
    void testExecuteQueryWithErrorHandler() {
        boolean[] errorCaught = {false};

        List<TestEntity> results = repository.executeQuery(
                "SELECT * FROM non_existent_table",
                e -> errorCaught[0] = true
        );

        assertTrue(errorCaught[0], "Error handler should have been called");
        assertTrue(results.isEmpty(), "Should return empty list on error");
    }

    @Test
    void testExecuteUpdateWithErrorHandler() {
        boolean[] errorCaught = {false};

        repository.executeUpdate(
                "UPDATE non_existent_table SET col = 'value'",
                e -> errorCaught[0] = true
        );

        assertTrue(errorCaught[0], "Error handler should have been called");
    }
}