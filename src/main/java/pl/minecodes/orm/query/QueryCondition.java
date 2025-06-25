package pl.minecodes.orm.query;

public class QueryCondition {

  private final LogicalOperator logicalOperator;
  private final String field;
  private final Operator operator;
  private final Object value;

  public QueryCondition(String field, Operator operator, Object value) {
    this(LogicalOperator.AND, field, operator, value);
  }

  public QueryCondition(LogicalOperator logicalOperator, String field, Operator operator, Object value) {
    this.logicalOperator = logicalOperator;
    this.field = field;
    this.operator = operator;
    this.value = value;
  }

  public LogicalOperator getLogicalOperator() {
    return logicalOperator;
  }

  public String getField() {
    return field;
  }

  public Operator getOperator() {
    return operator;
  }

  public Object getValue() {
    return value;
  }
}