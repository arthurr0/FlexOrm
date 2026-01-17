package pl.minecodes.orm.relation;

import org.junit.jupiter.api.Test;
import pl.minecodes.orm.annotation.FetchType;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class RelationInfoTest {

    @Test
    void testIsOwningWithJoinColumn() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.MANY_TO_ONE,
                Object.class,
                "user_id",
                null,
                null,
                null,
                FetchType.EAGER,
                false
        );

        assertTrue(info.isOwning());
        assertFalse(info.isInverse());
    }

    @Test
    void testIsInverseWithMappedBy() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.ONE_TO_MANY,
                Object.class,
                null,
                "author",
                null,
                null,
                FetchType.LAZY,
                true
        );

        assertFalse(info.isOwning());
        assertTrue(info.isInverse());
    }

    @Test
    void testNeitherOwningNorInverse() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.MANY_TO_MANY,
                Object.class,
                null,
                null,
                "join_table",
                "inverse_col",
                FetchType.EAGER,
                false
        );

        assertFalse(info.isOwning());
        assertFalse(info.isInverse());
    }

    @Test
    void testRelationTypeOneToOne() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.ONE_TO_ONE,
                Object.class,
                "profile_id",
                null,
                null,
                null,
                FetchType.EAGER,
                true
        );

        assertEquals(RelationType.ONE_TO_ONE, info.type());
        assertTrue(info.cascade());
    }

    @Test
    void testRelationTypeOneToMany() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.ONE_TO_MANY,
                Object.class,
                null,
                "parent",
                null,
                null,
                FetchType.LAZY,
                false
        );

        assertEquals(RelationType.ONE_TO_MANY, info.type());
        assertEquals(FetchType.LAZY, info.fetchType());
    }

    @Test
    void testRelationTypeManyToOne() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.MANY_TO_ONE,
                Object.class,
                "category_id",
                null,
                null,
                null,
                FetchType.EAGER,
                false
        );

        assertEquals(RelationType.MANY_TO_ONE, info.type());
        assertEquals("category_id", info.joinColumn());
    }

    @Test
    void testRelationTypeManyToMany() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.MANY_TO_MANY,
                Object.class,
                null,
                null,
                "user_roles",
                "role_id",
                FetchType.LAZY,
                true
        );

        assertEquals(RelationType.MANY_TO_MANY, info.type());
        assertEquals("user_roles", info.joinTable());
        assertEquals("role_id", info.inverseJoinColumn());
    }

    @Test
    void testEmptyJoinColumnIsNotOwning() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.MANY_TO_ONE,
                Object.class,
                "",
                null,
                null,
                null,
                FetchType.EAGER,
                false
        );

        assertFalse(info.isOwning());
    }

    @Test
    void testEmptyMappedByIsNotInverse() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.ONE_TO_MANY,
                Object.class,
                null,
                "",
                null,
                null,
                FetchType.LAZY,
                false
        );

        assertFalse(info.isInverse());
    }

    @Test
    void testTargetEntity() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.MANY_TO_ONE,
                String.class,
                "str_id",
                null,
                null,
                null,
                FetchType.EAGER,
                false
        );

        assertEquals(String.class, info.targetEntity());
    }

    @Test
    void testFieldAccess() throws NoSuchFieldException {
        Field field = TestClass.class.getDeclaredField("testField");
        RelationInfo info = new RelationInfo(
                field,
                RelationType.ONE_TO_ONE,
                Object.class,
                "obj_id",
                null,
                null,
                null,
                FetchType.EAGER,
                false
        );

        assertEquals(field, info.field());
        assertEquals("testField", info.field().getName());
    }

    static class TestClass {
        private String testField;
    }
}
