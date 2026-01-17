package pl.minecodes.orm.entity;

import java.util.ArrayList;
import java.util.List;
import pl.minecodes.orm.annotation.FetchType;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.annotation.OrmOneToMany;

@OrmEntity(table = "authors")
public class AuthorEntity {

  @OrmEntityId
  private Long id;

  @OrmField
  private String name;

  @OrmOneToMany(targetEntity = BookEntity.class, mappedBy = "author", fetch = FetchType.EAGER, cascade = true)
  private List<BookEntity> books = new ArrayList<>();

  public AuthorEntity() {
  }

  public AuthorEntity(String name) {
    this.name = name;
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

  public List<BookEntity> getBooks() {
    return books;
  }

  public void setBooks(List<BookEntity> books) {
    this.books = books;
  }
}
