package io.github.open_policy_agent.opa.jackson;

import io.github.open_policy_agent.opa.ir.Operand;
import io.github.open_policy_agent.opa.ir.policy.types.Type;
import io.github.open_policy_agent.opa.ir.stmts.Stmt;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson {@link SimpleModule} that registers the OPA IR deserializers.
 *
 * <p>Register this module on any {@link tools.jackson.databind.ObjectMapper} that will
 * parse OPA IR policy documents:
 *
 * <pre>{@code
 * JsonMapper mapper = JsonMapper.builder().addModule(new IrModule()).build();
 * }</pre>
 */
public class IrModule extends SimpleModule {

  public IrModule() {
    super("opa-ir");
    addDeserializer(Operand.class, new OperandDeserializer());
    addDeserializer(Stmt.class, new StmtDeserializer());
    addDeserializer(Type.class, new TypeDeserializer());
  }
}
