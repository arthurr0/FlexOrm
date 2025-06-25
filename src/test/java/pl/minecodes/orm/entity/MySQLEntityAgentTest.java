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
class MySQLEntityAgentTest {

    @Container
    private static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    private FlexOrm flexOrm;
    private EntityAgent<TestEntity, Long> entityAgent;

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
        entityAgent = flexOrm.getEntityAgent(TestEntity.class);

        // Create table for TestEntity
        entityAgent.executeUpdate("CREATE TABLE IF NOT EXISTS testentity (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), age INT, active BOOLEAN)");

    }

    @AfterEach
    void tearDown() {
        entityAgent.executeUpdate("DROP TABLE IF EXISTS testentity");
    }

    @Test
    void testSaveAndFindById() {
        TestEntity entity = new TestEntity("Test Name", 25, true);
        entityAgent.save(entity);

        assertNotNull(entity.getId(), "ID should be set after saving");

        Optional<TestEntity> foundEntity = entityAgent.findById(entity.getId());
        assertTrue(foundEntity.isPresent(), "Entity should be found by ID");
        assertEquals(entity.getName(), foundEntity.get().getName());
        assertEquals(entity.getAge(), foundEntity.get().getAge());
        assertEquals(entity.isActive(), foundEntity.get().isActive());
    }

    @Test
    void testUpdate() {
        TestEntity entity = new TestEntity("Initial Name", 30, true);
        entityAgent.save(entity);
        Long id = entity.getId();

        entity.setName("Updated Name");
        entity.setAge(31);
        entity.setActive(false);
        entityAgent.update(entity);

        Optional<TestEntity> foundEntity = entityAgent.findById(id);
        assertTrue(foundEntity.isPresent());
        assertEquals("Updated Name", foundEntity.get().getName());
        assertEquals(31, foundEntity.get().getAge());
        assertFalse(foundEntity.get().isActive());
    }

    @Test
    void testDelete() {
        TestEntity entity = new TestEntity("To Delete", 40, true);
        entityAgent.save(entity);
        Long id = entity.getId();

        assertTrue(entityAgent.findById(id).isPresent());

        entityAgent.delete(entity);

        assertFalse(entityAgent.findById(id).isPresent());
    }

    @Test
    void testDeleteById() {
        TestEntity entity = new TestEntity("To Delete By ID", 41, false);
        entityAgent.save(entity);
        Long id = entity.getId();

        assertTrue(entityAgent.findById(id).isPresent());

        entityAgent.deleteById(id);

        assertFalse(entityAgent.findById(id).isPresent());
    }

    @Test
    void testFindAll() {
        entityAgent.save(new TestEntity("Entity 1", 25, true));
        entityAgent.save(new TestEntity("Entity 2", 30, false));
        entityAgent.save(new TestEntity("Entity 3", 35, true));

        List<TestEntity> entities = entityAgent.findAll();

        assertEquals(3, entities.size());
    }

    @Test
    void testFindByField() {
        entityAgent.save(new TestEntity("Active Entity 1", 25, true));
        entityAgent.save(new TestEntity("Inactive Entity", 30, false));
        entityAgent.save(new TestEntity("Active Entity 2", 35, true));

        List<TestEntity> activeEntities = entityAgent.findByField("active", true);

        assertEquals(2, activeEntities.size());
        for (TestEntity entity : activeEntities) {
            assertTrue(entity.isActive());
        }
    }

    @Test
    void testExecuteRawQuery() {
        entityAgent.save(new TestEntity("Raw Query Test", 50, true));

        List<TestEntity> results = entityAgent.executeQuery("SELECT * FROM testentity WHERE age = 50");

        assertEquals(1, results.size());
        assertEquals("Raw Query Test", results.get(0).getName());
    }

    @Test
    void testTransaction() {
        entityAgent.beginTransaction();

        try {
            entityAgent.save(new TestEntity("Transaction Entity 1", 60, true));
            entityAgent.save(new TestEntity("Transaction Entity 2", 61, false));
            entityAgent.commitTransaction();
        } catch (Exception e) {
            entityAgent.rollbackTransaction();
        }

        List<TestEntity> entities = entityAgent.findAll();
        assertEquals(2, entities.size());
    }

    @Test
    void testTransactionRollback() {
        entityAgent.beginTransaction();

        try {
            entityAgent.save(new TestEntity("Transaction Entity 1", 70, true));
            entityAgent.save(new TestEntity("Transaction Entity 2", 71, false));
            entityAgent.rollbackTransaction();
        } catch (Exception e) {
            entityAgent.rollbackTransaction();
        }

        List<TestEntity> entities = entityAgent.findAll();
        assertEquals(0, entities.size());
    }

    @Test
    void testQueryBuilder() {
        entityAgent.save(new TestEntity("Query Builder 1", 80, true));
        entityAgent.save(new TestEntity("Query Builder 2", 81, false));
        entityAgent.save(new TestEntity("Query Builder 3", 82, true));

        List<TestEntity> result = entityAgent.query()
                .where("age", Operator.GREATER_THAN, 80)
                .execute();

        assertEquals(2, result.size());
    }

    @Test
    void testExecuteQueryWithErrorHandler() {
        boolean[] errorCaught = {false};

        List<TestEntity> results = entityAgent.executeQuery(
                "SELECT * FROM non_existent_table",
                e -> errorCaught[0] = true
        );

        assertTrue(errorCaught[0], "Error handler should have been called");
        assertTrue(results.isEmpty(), "Should return empty list on error");
    }

    @Test
    void testExecuteUpdateWithErrorHandler() {
        boolean[] errorCaught = {false};

        entityAgent.executeUpdate(
                "UPDATE non_existent_table SET col = 'value'",
                e -> errorCaught[0] = true
        );

        assertTrue(errorCaught[0], "Error handler should have been called");
    }
}