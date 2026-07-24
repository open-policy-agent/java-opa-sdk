package policy["pr-check_test"]

import data.policy["pr-check"] as pr_check

example_evaluator_changelist := [
	{"filename": "opa-evaluator/src/main/java/com/example/Evaluator.java"},
	{"filename": "opa-evaluator/build.gradle.kts"},
]

example_jackson_changelist := [
	{"filename": "opa-jackson/src/main/java/com/example/Jackson.java"},
]

example_services_changelist := [
	{"filename": "opa-services/src/test/java/com/example/ServiceTest.java"},
]

example_builtins_changelist := [
	{"filename": "opa-builtins/opa-builtins-time/src/main/java/Time.java"},
]

example_slf4j_changelist := [
	{"filename": "opa-slf4j/src/main/java/com/example/Logger.java"},
]

example_proto_changelist := [
	{"filename": "opa-proto/src/main/java/com/example/PlanMapper.java"},
]

example_generator_changelist := [
	{"filename": "tools/generate-compliance-tests/go.mod"},
]

example_build_changelist := [
	{"filename": "build.gradle.kts"},
]

example_gradle_changelist := [
	{"filename": "gradle/wrapper/gradle-wrapper.properties"},
]

example_unrelated_changelist := [
	{"filename": "README.md"},
	{"filename": "LICENSE"},
]

test_evaluator_change_triggers_evaluator if {
	pr_check.changes["opa-evaluator"] with input as example_evaluator_changelist
}

test_evaluator_change_does_not_trigger_others if {
	not pr_check.changes["opa-jackson"] with input as example_evaluator_changelist
	not pr_check.changes["opa-services"] with input as example_evaluator_changelist
	not pr_check.changes["opa-builtins"] with input as example_evaluator_changelist
	not pr_check.changes["opa-slf4j"] with input as example_evaluator_changelist
	not pr_check.changes["opa-proto"] with input as example_evaluator_changelist
}

test_jackson_change_triggers_jackson if {
	pr_check.changes["opa-jackson"] with input as example_jackson_changelist
}

test_services_change_triggers_services if {
	pr_check.changes["opa-services"] with input as example_services_changelist
}

test_builtins_change_triggers_builtins if {
	pr_check.changes["opa-builtins"] with input as example_builtins_changelist
}

test_slf4j_change_triggers_slf4j if {
	pr_check.changes["opa-slf4j"] with input as example_slf4j_changelist
}

test_proto_change_triggers_proto if {
	pr_check.changes["opa-proto"] with input as example_proto_changelist
}

test_generator_change_triggers_proto if {
	pr_check.changes["opa-proto"] with input as example_generator_changelist
}

test_build_file_triggers_all if {
	pr_check.changes["opa-evaluator"] with input as example_build_changelist
	pr_check.changes["opa-jackson"] with input as example_build_changelist
	pr_check.changes["opa-services"] with input as example_build_changelist
	pr_check.changes["opa-builtins"] with input as example_build_changelist
	pr_check.changes["opa-slf4j"] with input as example_build_changelist
	pr_check.changes["opa-proto"] with input as example_build_changelist
}

test_gradle_dir_triggers_all if {
	pr_check.changes["opa-evaluator"] with input as example_gradle_changelist
	pr_check.changes["opa-jackson"] with input as example_gradle_changelist
	pr_check.changes["opa-services"] with input as example_gradle_changelist
	pr_check.changes["opa-builtins"] with input as example_gradle_changelist
	pr_check.changes["opa-slf4j"] with input as example_gradle_changelist
	pr_check.changes["opa-proto"] with input as example_gradle_changelist
}

test_unrelated_change_triggers_none if {
	not pr_check.changes["opa-evaluator"] with input as example_unrelated_changelist
	not pr_check.changes["opa-jackson"] with input as example_unrelated_changelist
	not pr_check.changes["opa-services"] with input as example_unrelated_changelist
	not pr_check.changes["opa-builtins"] with input as example_unrelated_changelist
	not pr_check.changes["opa-slf4j"] with input as example_unrelated_changelist
	not pr_check.changes["opa-proto"] with input as example_unrelated_changelist
}
