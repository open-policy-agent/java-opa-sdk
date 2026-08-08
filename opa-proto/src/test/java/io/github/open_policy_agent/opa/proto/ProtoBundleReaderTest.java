package io.github.open_policy_agent.opa.proto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import opa.ir.v1.AssignVarStmt;
import opa.ir.v1.Block;
import opa.ir.v1.Operand;
import opa.ir.v1.Plan;
import opa.ir.v1.Plans;
import opa.ir.v1.Policy;
import opa.ir.v1.Stmt;
import org.junit.jupiter.api.Test;

/** Verifies decode-failure error handling for the proto plan reader. */
class ProtoBundleReaderTest {

  @Test
  void malformedPlanIsReportedAsIoException() {
    // An AssignVarStmt whose source operand has no value set — a malformed plan. PlanMapper rejects
    // it with IllegalArgumentException; decodePlan must surface that as IOException.
    Policy proto =
        Policy.newBuilder()
            .setPlans(
                Plans.newBuilder()
                    .addPlans(
                        Plan.newBuilder()
                            .setName("p")
                            .addBlocks(
                                Block.newBuilder()
                                    .addStmts(
                                        Stmt.newBuilder()
                                            .setAssignVarStmt(
                                                AssignVarStmt.newBuilder()
                                                    .setSource(Operand.newBuilder().build())
                                                    .setTarget(0))))))
            .build();
    byte[] bytes = proto.toByteArray();

    assertThatThrownBy(() -> new ProtoBundleReader().decodePlan(new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("malformed proto plan");
  }
}
