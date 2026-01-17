package pl.minecodes.orm.query;

public record QueryCondition(LogicalOperator logicalOperator, String field, Operator operator,
                             Object value) {

  public QueryCondition(String field, Operator operator, Object value) {
    this(LogicalOperator.AND, field, operator, value);
  }

}