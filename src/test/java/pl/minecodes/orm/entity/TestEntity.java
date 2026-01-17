package pl.minecodes.orm.entity;

import java.util.Objects;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;

@OrmEntity(table = "testentity")
public class TestEntity {

  @OrmEntityId
  private Long id;

  @OrmField
  private String name;

  @OrmField
  private int age;

  @OrmField
  private boolean active;

  public TestEntity() {
  }

  public TestEntity(String name, int age, boolean active) {
    this.name = name;
    this.age = age;
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

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TestEntity that = (TestEntity) o;

    if (age != that.age) {
      return false;
    }
    if (active != that.active) {
      return false;
    }
    if (!Objects.equals(id, that.id)) {
      return false;
    }
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + age;
    result = 31 * result + (active ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "TestEntity{" +
        "id=" + id +
        ", name='" + name + '\'' +
        ", age=" + age +
        ", active=" + active +
        '}';
  }
}
