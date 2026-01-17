package pl.minecodes.orm.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.minecodes.orm.FlexOrm;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    @TempDir
    Path tempDir;

    private FlexOrm flexOrm;
    private EntityRepository<TransactionEntity, Long> agent;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("transaction-test.db").toFile();
        flexOrm = FlexOrm.sqllite(dbFile);
        flexOrm.connect();
        agent = flexOrm.getEntityRepository(TransactionEntity.class);
        agent.executeUpdate("CREATE TABLE IF NOT EXISTS transaction_test (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, value INTEGER)");
    }

    @Test
    void testCommitTransaction() {
        agent.beginTransaction();

        TransactionEntity entity1 = new TransactionEntity("Entity1", 100);
        TransactionEntity entity2 = new TransactionEntity("Entity2", 200);
        agent.save(entity1);
        agent.save(entity2);

        agent.commitTransaction();

        List<TransactionEntity> found = agent.findAll();
        assertEquals(2, found.size());
    }

    @Test
    void testRollbackTransaction() {
        agent.beginTransaction();

        TransactionEntity entity1 = new TransactionEntity("Entity1", 100);
        TransactionEntity entity2 = new TransactionEntity("Entity2", 200);
        agent.save(entity1);
        agent.save(entity2);

        agent.rollbackTransaction();

        List<TransactionEntity> found = agent.findAll();
        assertEquals(0, found.size());
    }

    @Test
    void testTransactionIsolation() {
        TransactionEntity preExisting = new TransactionEntity("PreExisting", 50);
        agent.save(preExisting);

        agent.beginTransaction();

        TransactionEntity newEntity = new TransactionEntity("InTransaction", 100);
        agent.save(newEntity);

        agent.rollbackTransaction();

        List<TransactionEntity> found = agent.findAll();
        assertEquals(1, found.size());
        assertEquals("PreExisting", found.get(0).getName());
    }

    @Test
    void testMultipleOperationsInTransaction() {
        TransactionEntity entity = new TransactionEntity("Original", 100);
        agent.save(entity);

        agent.beginTransaction();

        entity.setName("Updated");
        entity.setValue(200);
        agent.update(entity);

        TransactionEntity newEntity = new TransactionEntity("New", 300);
        agent.save(newEntity);

        agent.commitTransaction();

        List<TransactionEntity> found = agent.findAll();
        assertEquals(2, found.size());
    }

    @Test
    void testDeleteInTransaction() {
        TransactionEntity entity1 = new TransactionEntity("ToDelete", 100);
        TransactionEntity entity2 = new TransactionEntity("ToKeep", 200);
        agent.save(entity1);
        agent.save(entity2);

        agent.beginTransaction();
        agent.delete(entity1);
        agent.commitTransaction();

        List<TransactionEntity> found = agent.findAll();
        assertEquals(1, found.size());
        assertEquals("ToKeep", found.get(0).getName());
    }

    @Test
    void testDeleteInTransactionRollback() {
        TransactionEntity entity1 = new TransactionEntity("ToDelete", 100);
        TransactionEntity entity2 = new TransactionEntity("ToKeep", 200);
        agent.save(entity1);
        agent.save(entity2);

        agent.beginTransaction();
        agent.delete(entity1);
        agent.rollbackTransaction();

        List<TransactionEntity> found = agent.findAll();
        assertEquals(2, found.size());
    }

    @Test
    void testBeginTransactionTwiceThrows() {
        agent.beginTransaction();

        assertThrows(IllegalStateException.class, () -> agent.beginTransaction());

        agent.rollbackTransaction();
    }

    @Test
    void testCommitWithoutTransactionThrows() {
        assertThrows(IllegalStateException.class, () -> agent.commitTransaction());
    }

    @Test
    void testRollbackWithoutTransactionThrows() {
        assertThrows(IllegalStateException.class, () -> agent.rollbackTransaction());
    }

    @Test
    void testTransactionAfterCommit() {
        agent.beginTransaction();
        agent.save(new TransactionEntity("First", 100));
        agent.commitTransaction();

        agent.beginTransaction();
        agent.save(new TransactionEntity("Second", 200));
        agent.commitTransaction();

        List<TransactionEntity> found = agent.findAll();
        assertEquals(2, found.size());
    }

    @Test
    void testTransactionAfterRollback() {
        agent.beginTransaction();
        agent.save(new TransactionEntity("RolledBack", 100));
        agent.rollbackTransaction();

        agent.beginTransaction();
        agent.save(new TransactionEntity("Committed", 200));
        agent.commitTransaction();

        List<TransactionEntity> found = agent.findAll();
        assertEquals(1, found.size());
        assertEquals("Committed", found.get(0).getName());
    }

    @Test
    void testUpdateInTransaction() {
        TransactionEntity entity = new TransactionEntity("Original", 100);
        agent.save(entity);

        agent.beginTransaction();
        entity.setValue(999);
        agent.update(entity);
        agent.commitTransaction();

        TransactionEntity found = agent.findById(entity.getId()).orElseThrow();
        assertEquals(999, found.getValue());
    }

    @Test
    void testUpdateInTransactionRollback() {
        TransactionEntity entity = new TransactionEntity("Original", 100);
        agent.save(entity);

        agent.beginTransaction();
        entity.setValue(999);
        agent.update(entity);
        agent.rollbackTransaction();

        TransactionEntity found = agent.findById(entity.getId()).orElseThrow();
        assertEquals(100, found.getValue());
    }

    @Test
    void testMixedOperationsTransaction() {
        TransactionEntity e1 = new TransactionEntity("E1", 1);
        TransactionEntity e2 = new TransactionEntity("E2", 2);
        TransactionEntity e3 = new TransactionEntity("E3", 3);
        agent.save(e1);
        agent.save(e2);
        agent.save(e3);

        agent.beginTransaction();
        agent.delete(e1);
        e2.setValue(22);
        agent.update(e2);
        TransactionEntity e4 = new TransactionEntity("E4", 4);
        agent.save(e4);
        agent.commitTransaction();

        List<TransactionEntity> found = agent.findAll();
        assertEquals(3, found.size());
    }

    @OrmEntity(table = "transaction_test")
    public static class TransactionEntity {
        @OrmEntityId
        private Long id;

        @OrmField
        private String name;

        @OrmField
        private int value;

        public TransactionEntity() {}

        public TransactionEntity(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
}
