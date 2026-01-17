package pl.minecodes.orm.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHierarchyTest {

    @Test
    void testOrmExceptionIsRuntimeException() {
        OrmException exception = new OrmException("Test message");
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testOrmExceptionMessage() {
        OrmException exception = new OrmException("Test error message");
        assertEquals("Test error message", exception.getMessage());
    }

    @Test
    void testOrmExceptionWithCause() {
        Exception cause = new IllegalArgumentException("Root cause");
        OrmException exception = new OrmException("Wrapper message", cause);

        assertEquals("Wrapper message", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testValidationExceptionExtendsOrmException() {
        ValidationException exception = new ValidationException("Validation failed");
        assertInstanceOf(OrmException.class, exception);
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testValidationExceptionMessage() {
        ValidationException exception = new ValidationException("Field cannot be null");
        assertEquals("Field cannot be null", exception.getMessage());
    }

    @Test
    void testValidationExceptionWithCause() {
        Exception cause = new NullPointerException("null value");
        ValidationException exception = new ValidationException("Validation error", cause);

        assertEquals("Validation error", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testConnectionExceptionExtendsOrmException() {
        ConnectionException exception = new ConnectionException("Connection failed");
        assertInstanceOf(OrmException.class, exception);
    }

    @Test
    void testConnectionExceptionMessage() {
        ConnectionException exception = new ConnectionException("Unable to connect to database");
        assertEquals("Unable to connect to database", exception.getMessage());
    }

    @Test
    void testConnectionExceptionWithCause() {
        Exception cause = new java.sql.SQLException("Network error");
        ConnectionException exception = new ConnectionException("Connection error", cause);

        assertEquals("Connection error", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testQueryExceptionExtendsOrmException() {
        QueryException exception = new QueryException("Query failed");
        assertInstanceOf(OrmException.class, exception);
    }

    @Test
    void testQueryExceptionMessage() {
        QueryException exception = new QueryException("Invalid SQL syntax");
        assertEquals("Invalid SQL syntax", exception.getMessage());
    }

    @Test
    void testQueryExceptionWithCause() {
        Exception cause = new java.sql.SQLException("Syntax error");
        QueryException exception = new QueryException("Query execution error", cause);

        assertEquals("Query execution error", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testEntityNotFoundExceptionExtendsOrmException() {
        EntityNotFoundException exception = new EntityNotFoundException("Entity not found");
        assertInstanceOf(OrmException.class, exception);
    }

    @Test
    void testEntityNotFoundExceptionWithClassAndId() {
        EntityNotFoundException exception = new EntityNotFoundException(String.class, 123L);

        assertTrue(exception.getMessage().contains("String"));
        assertTrue(exception.getMessage().contains("123"));
    }

    @Test
    void testEntityNotFoundExceptionMessageFormat() {
        EntityNotFoundException exception = new EntityNotFoundException(TestEntity.class, 42);

        assertEquals("Entity TestEntity with id 42 not found", exception.getMessage());
    }

    @Test
    void testTransactionExceptionExtendsOrmException() {
        TransactionException exception = new TransactionException("Transaction failed");
        assertInstanceOf(OrmException.class, exception);
    }

    @Test
    void testTransactionExceptionMessage() {
        TransactionException exception = new TransactionException("Rollback error");
        assertEquals("Rollback error", exception.getMessage());
    }

    @Test
    void testTransactionExceptionWithCause() {
        Exception cause = new java.sql.SQLException("Deadlock");
        TransactionException exception = new TransactionException("Transaction aborted", cause);

        assertEquals("Transaction aborted", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testCatchOrmExceptionCatchesAllSubtypes() {
        try {
            throw new ValidationException("test");
        } catch (OrmException e) {
            assertInstanceOf(ValidationException.class, e);
        }

        try {
            throw new ConnectionException("test");
        } catch (OrmException e) {
            assertInstanceOf(ConnectionException.class, e);
        }

        try {
            throw new QueryException("test");
        } catch (OrmException e) {
            assertInstanceOf(QueryException.class, e);
        }

        try {
            throw new EntityNotFoundException("test");
        } catch (OrmException e) {
            assertInstanceOf(EntityNotFoundException.class, e);
        }

        try {
            throw new TransactionException("test");
        } catch (OrmException e) {
            assertInstanceOf(TransactionException.class, e);
        }
    }

    @Test
    void testExceptionChaining() {
        Exception root = new IllegalStateException("Root");
        OrmException middle = new QueryException("Middle", root);
        OrmException top = new OrmException("Top", middle);

        assertEquals("Top", top.getMessage());
        assertSame(middle, top.getCause());
        assertSame(root, top.getCause().getCause());
    }

    static class TestEntity {}
}
