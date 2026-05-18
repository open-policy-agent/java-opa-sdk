package io.github.open_policy_agent.opa.ir;

import static org.junit.jupiter.api.Assertions.*;

import io.github.open_policy_agent.opa.ir.PolicyReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ir.policy.*;
import io.github.open_policy_agent.opa.ir.policy.types.*;
import io.github.open_policy_agent.opa.ir.stmts.*;
import io.github.open_policy_agent.opa.ir.vals.BoolVal;
import io.github.open_policy_agent.opa.ir.vals.LocalVal;
import io.github.open_policy_agent.opa.ir.vals.StringIndexVal;

class PolicyTest {
  private static final PolicyReader policyReader =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();

  @Test
  public void testPolicyDeserialization_happy_path() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass().getClassLoader().getResource("ir/testdata/policy-happy-path.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    Static goldenStaticData = new Static();

    goldenStaticData.setFiles(List.of(new StringConst("policy.rego")));

    goldenStaticData.setStrings(List.of(new StringConst("result"), new StringConst("testing")));

    goldenStaticData.setBuiltinFuncs(
        List.of(
            new BuiltinFunc(
                "count",
                new FunctionType(
                    List.of(
                        new AnyType(
                            List.of(
                                new StringType(),
                                new ArrayType(null, new AnyType()),
                                new ObjectType(
                                    null,
                                    new ObjectTypeDynamicProperty(new AnyType(), new AnyType())),
                                new SetType(new AnyType())))),
                    new NumberType())),
            new BuiltinFunc(
                "internal.member_2",
                new FunctionType(List.of(new AnyType(), new AnyType()), new BooleanType())),
            new BuiltinFunc(
                "rem",
                new FunctionType(List.of(new NumberType(), new NumberType()), new NumberType()))));

    ArrayAppendStmt arrayAppendStmt =
        new ArrayAppendStmt(new Operand(new StringIndexVal(9)), 4);
    arrayAppendStmt.setLocation(0, 34, 16);
    Func goldenFunc =
        new Func(
            "g0.data.policy.hello",
            List.of(0, 1),
            2,
            List.of(new Block(List.of(arrayAppendStmt))),
            List.of("g0", "policy", "hello"));
    Funcs goldenFuncs = new Funcs(List.of(goldenFunc));

    MakeObjectStmt expectedStmt = new MakeObjectStmt(2);
    expectedStmt.setLocation(0, 0, 0);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));

    Policy golden = new Policy();
    golden.setStatic(goldenStaticData);
    golden.setFuncs(goldenFuncs);
    golden.setPlans(plans);
    assertEquals(golden, policy);
  }

  @Test
  public void testPolicyDeserialization_staticsOnlyStrings() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-static-only-strings.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    Static goldenStaticData = new Static();
    goldenStaticData.setStrings(
        List.of(
            new StringConst("result"),
            new StringConst("amir"),
            new StringConst("foo"),
            new StringConst("bar"),
            new StringConst("100"),
            new StringConst("101"),
            new StringConst("43"),
            new StringConst("42"),
            new StringConst("john"),
            new StringConst("hello"),
            new StringConst("admin"),
            new StringConst("user"),
            new StringConst("roles"),
            new StringConst("m"),
            new StringConst("y"),
            new StringConst("1"),
            new StringConst("yolo"),
            new StringConst("policy")));

    Policy golden = new Policy();
    golden.setStatic(goldenStaticData);
    assertEquals(golden, policy);
  }

  @Test
  public void testPolicyDeserialization_staticsOnlyBuiltinFuncs() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-static-only-builtin_funcs.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));

    Static goldenStatic = new Static();
    goldenStatic.setBuiltinFuncs(
        List.of(
            new BuiltinFunc(
                "count",
                new FunctionType(
                    List.of(
                        new AnyType(
                            List.of(
                                new StringType(),
                                new ArrayType(null, new AnyType()),
                                new ObjectType(
                                    null,
                                    new ObjectTypeDynamicProperty(new AnyType(), new AnyType())),
                                new SetType(new AnyType())))),
                    new NumberType())),
            new BuiltinFunc(
                "internal.member_2",
                new FunctionType(List.of(new AnyType(), new AnyType()), new BooleanType())),
            new BuiltinFunc(
                "rem",
                new FunctionType(List.of(new NumberType(), new NumberType()), new NumberType()))));

    Policy golden = new Policy();
    golden.setStatic(goldenStatic);
    assertEquals(golden, policy);
  }

  @Test
  public void testPolicyDeserialization_funcsOnly() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass().getClassLoader().getResource("ir/testdata/policy-only-funcs.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    ArrayAppendStmt arrayAppendStmt =
        new ArrayAppendStmt(new Operand(new StringIndexVal(9)), 4);
    arrayAppendStmt.setLocation(0, 34, 16);

    Func goldenFunc =
        new Func(
            "g0.data.policy.hello",
            List.of(0, 1),
            2,
            List.of(new Block(List.of(arrayAppendStmt))),
            List.of("g0", "policy", "hello"));

    Funcs goldenFuncs = new Funcs();
    goldenFuncs.setFuncs(List.of(goldenFunc));
    Policy golden = new Policy();
    golden.setFuncs(goldenFuncs);
    assertEquals(golden, policy);
  }

  @Test
  public void testPolicyDeserialization_ArrayAppendStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-ArrayAppendStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    ArrayAppendStmt arrayAppendStmt =
        new ArrayAppendStmt(new Operand(new StringIndexVal("string_index", 9)), 4);

    arrayAppendStmt.setLocation(0, 34, 16);
    Block block = new Block(List.of(arrayAppendStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_AssignIntStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-AssignIntStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    AssignIntStmt expectedAssignIntStmt = new AssignIntStmt(10L, 4);

    expectedAssignIntStmt.setLocation(0, 34, 16);
    Block block = new Block(List.of(expectedAssignIntStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_AssignVarOnceStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-AssignVarOnceStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");
    Operand source = new Operand(new LocalVal(10));
    AssignVarOnceStmt expectedAssignVarOnceStmt = new AssignVarOnceStmt(source, 5);

    expectedAssignVarOnceStmt.setLocation(0, 48, 1);
    Block block = new Block(List.of(expectedAssignVarOnceStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_AssignVarStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-AssignVarStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");
    Operand source = new Operand(new BoolVal(true));
    AssignVarStmt expectedAssignVarStmt = new AssignVarStmt(source, 5);

    expectedAssignVarStmt.setLocation(0, 48, 1);
    Block block = new Block(List.of(expectedAssignVarStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_BlockStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-BlockStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");
    AssignVarStmt expectedAssignVarStmt = new AssignVarStmt(new Operand(new BoolVal(true)), 5);
    expectedAssignVarStmt.setLocation(0, 48, 1);
    Block block = new Block(List.of(expectedAssignVarStmt));
    BlockStmt expectedBlockStmt = new BlockStmt(List.of(block));
    expectedBlockStmt.setLocation(1, 3, 2);

    Block planBlock = new Block(List.of(expectedBlockStmt));
    Plan plan = new Plan("policy", List.of(planBlock));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_BreakStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-BreakStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");
    BreakStmt expectedBreakStmt = new BreakStmt(1);
    expectedBreakStmt.setLocation(0, 0, 0);
    Block block = new Block(List.of(expectedBreakStmt));
    BlockStmt expectedBlockStmt = new BlockStmt(List.of(block));
    expectedBlockStmt.setLocation(1, 3, 2);

    Block planBlock = new Block(List.of(expectedBlockStmt));
    Plan plan = new Plan("policy", List.of(planBlock));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_CallDynamicStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-CallDynamicStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");
    List<Operand> path =
        new ArrayList<>() {
          {
            add(new Operand(new LocalVal(10)));
            add(new Operand(new StringIndexVal(5)));
          }
        };
    List<Integer> args =
        new ArrayList<>() {
          {
            add(0);
            add(1);
            add(2);
            add(3);
          }
        };

    CallDynamicStmt expectedStmt = new CallDynamicStmt(path, args, 5);
    expectedStmt.setLocation(0, 48, 1);
    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_CallStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-CallStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");
    List<Operand> args =
        new ArrayList<>() {
          {
            add(new Operand(new LocalVal(6)));
            add(new Operand(new LocalVal(7)));
          }
        };

    CallStmt expectedStmt = new CallStmt("plus", args, 8);
    expectedStmt.setLocation(50, 49, 10);
    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_DotStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-DotStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand source = new Operand(new LocalVal(1));
    Operand key = new Operand(new StringIndexVal(19));
    DotStmt expectedStmt = new DotStmt(source, key, 10);
    expectedStmt.setLocation(0, 0, 0);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_EqualStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-EqualStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand a = new Operand(new LocalVal(11));
    Operand b = new Operand(new BoolVal(false));
    EqualStmt expectedStmt = new EqualStmt(a, b);
    expectedStmt.setLocation(0, 34, 5);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_IsArrayStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-IsArrayStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand source = new Operand(new LocalVal(11));
    IsArrayStmt expectedStmt = new IsArrayStmt(source);
    expectedStmt.setLocation(0, 34, 5);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_IsDefinedStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-IsDefinedStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    IsDefinedStmt expectedStmt = new IsDefinedStmt(3);
    expectedStmt.setLocation(0, 33, 1);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_IsObjectStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-IsObjectStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand source = new Operand(new LocalVal(11));
    IsObjectStmt expectedStmt = new IsObjectStmt(source);
    expectedStmt.setLocation(0, 34, 5);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_IsUndefinedStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-IsUndefinedStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    IsUndefinedStmt expectedStmt = new IsUndefinedStmt(3);
    expectedStmt.setLocation(0, 33, 1);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_LenStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-LenStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand source = new Operand(new LocalVal(11));
    LenStmt expectedStmt = new LenStmt(source, 50);
    expectedStmt.setLocation(0, 34, 5);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_MakeArrayStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-MakeArrayStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    MakeArrayStmt expectedStmt = new MakeArrayStmt(30, 3);
    expectedStmt.setLocation(0, 33, 1);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_MakeNullStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-MakeNullStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    MakeNullStmt expectedStmt = new MakeNullStmt(3);
    expectedStmt.setLocation(0, 33, 1);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_MakeNumberIntStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-MakeNumberIntStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    MakeNumberIntStmt expectedStmt = new MakeNumberIntStmt(300, 3);
    expectedStmt.setLocation(0, 33, 1);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_MakeNumberRefStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-MakeNumberRefStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    MakeNumberRefStmt expectedStmt = new MakeNumberRefStmt(13, 4);
    expectedStmt.setLocation(0, 6, 1);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_MakeObjectStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-MakeObjectStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    MakeObjectStmt expectedStmt = new MakeObjectStmt(4);
    expectedStmt.setLocation(0, 7, 1);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_MakeSetStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-MakeSetStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    MakeSetStmt expectedStmt = new MakeSetStmt(3);
    expectedStmt.setLocation(0, 33, 1);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_NotEqualStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-NotEqualStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand a = new Operand(new LocalVal(11));
    Operand b = new Operand(new BoolVal(false));
    NotEqualStmt expectedStmt = new NotEqualStmt(a, b);
    expectedStmt.setLocation(0, 34, 5);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_NotStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-NotStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand a = new Operand(new LocalVal(4));
    Operand b = new Operand(new StringIndexVal(15));
    EqualStmt expectedEqualStmt = new EqualStmt(a, b);
    expectedEqualStmt.setLocation(0, 16, 5);
    NotStmt expectedNotStmt = new NotStmt(new Block(List.of(expectedEqualStmt)));
    expectedNotStmt.setLocation(0, 16, 5);

    Block block = new Block(List.of(expectedNotStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_ObjectInsertOnceStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-ObjectInsertOnceStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand key = new Operand(new LocalVal(11));
    Operand value = new Operand(new BoolVal(false));
    ObjectInsertOnceStmt expectedStmt = new ObjectInsertOnceStmt(key, value, 55);
    expectedStmt.setLocation(0, 34, 5);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_ObjectInsertStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-ObjectInsertStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand key = new Operand(new LocalVal(11));
    Operand value = new Operand(new BoolVal(false));
    ObjectInsertStmt expectedStmt = new ObjectInsertStmt(key, value, 55);
    expectedStmt.setLocation(0, 34, 5);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_ObjectMergeStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-ObjectMergeStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    ObjectMergeStmt expectedStmt = new ObjectMergeStmt(12, 2, 11);
    expectedStmt.setLocation(10, 22, 20);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_ResetLocalStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-ResetLocalStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    ResetLocalStmt expectedStmt = new ResetLocalStmt(5);
    expectedStmt.setLocation(0, 57, 1);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_ResultSetAddStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-ResultSetAddStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    ResultSetAddStmt expectedStmt = new ResultSetAddStmt(14);
    expectedStmt.setLocation(5, 7, 6);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_ReturnLocalStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-ReturnLocalStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    ReturnLocalStmt expectedStmt = new ReturnLocalStmt(2);
    expectedStmt.setLocation(0, 57, 1);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_ScanStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-ScanStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand source = new Operand(new LocalVal(11));
    AssignVarStmt expectedAssignVarStmt = new AssignVarStmt(source, 13);
    expectedAssignVarStmt.setLocation(0, 31, 14);
    ScanStmt expectedStmt = new ScanStmt(10, 11, 12, new Block(List.of(expectedAssignVarStmt)));
    expectedStmt.setLocation(0, 31, 14);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_SetAddStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-SetAddStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand value = new Operand(new LocalVal(13));
    SetAddStmt expectedStmt = new SetAddStmt(value, 14);
    expectedStmt.setLocation(1, 3, 2);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_WithStmt() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-WithStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand source = new Operand(new LocalVal(19));
    AssignVarOnceStmt expectedAssignVarOnceStmt = new AssignVarOnceStmt(source, 3);
    expectedAssignVarOnceStmt.setLocation(0, 30, 1);

    Operand value = new Operand(new LocalVal(8));
    List<Integer> path =
        new ArrayList<>() {
          {
            add(1);
            add(2);
          }
        };
    WithStmt expectedStmt =
        new WithStmt(0, path, value, new Block(List.of(expectedAssignVarOnceStmt)));
    expectedStmt.setLocation(0, 31, 9);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_WithStmt_null_path() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-only-plan-only-WithStmt-null-path.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");

    Operand source = new Operand(new LocalVal(19));
    AssignVarOnceStmt expectedAssignVarOnceStmt = new AssignVarOnceStmt(source, 3);
    expectedAssignVarOnceStmt.setLocation(0, 30, 1);

    Operand value = new Operand(new LocalVal(8));
    WithStmt expectedStmt =
        new WithStmt(10, null, value, new Block(List.of(expectedAssignVarOnceStmt)));
    expectedStmt.setLocation(0, 31, 9);

    Block block = new Block(List.of(expectedStmt));
    Plan plan = new Plan("policy", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy goldenPolicy = new Policy(null, plans, null);

    assertEquals(goldenPolicy, policy);
  }

  @Test
  public void testPolicyDeserialization_unknownFields() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-unknown-fields.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    assertNotNull(policy, "Policy should not be null");
    assertNull(policy.getFuncs());
    assertNull(policy.getStatic());
    assertNull(policy.getPlans());
  }
}
