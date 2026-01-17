package pl.minecodes.orm.entity;

import pl.minecodes.orm.annotation.FetchType;
import pl.minecodes.orm.annotation.OrmEntity;
import pl.minecodes.orm.annotation.OrmEntityId;
import pl.minecodes.orm.annotation.OrmField;
import pl.minecodes.orm.annotation.OrmManyToOne;

@OrmEntity(table = "books")
public class BookEntity {

  @OrmEntityId
  private Long id;

  @OrmField
  private String title;

  @OrmManyToOne(targetEntity = AuthorEntity.class, joinColumn = "author_id", fetch = FetchType.EAGER)
  private AuthorEntity author;

  public BookEntity() {
  }

  public BookEntity(String title) {
    this.title = title;
  }

  public BookEntity(String title, AuthorEntity author) {
    this.title = title;
    this.author = author;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public AuthorEntity getAuthor() {
    return author;
  }

  public void setAuthor(AuthorEntity author) {
    this.author = author;
  }
}
