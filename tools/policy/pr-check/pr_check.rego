package policy["pr-check"]

build_change_files := [
	"build.gradle.kts",
	"settings.gradle.kts",
	"gradle.properties",
]

changes["opa-evaluator"] if {
	some changed_file in input
	startswith(changed_file.filename, "opa-evaluator/")
} else if {
	# ComplianceTest lives in opa-evaluator but resolves builtins from the opa-builtins sub-modules
	# over the BuiltinProvider SPI, and fails on any fixture whose builtin it cannot resolve. A
	# builtins-only change (implementing a builtin, or registering its provider) therefore has to
	# re-run the compliance suite.
	some changed_file in input
	startswith(changed_file.filename, "opa-builtins/")
} else if {
	# An OPA version bump here regenerates the compliance fixtures the suite reads.
	some changed_file in input
	startswith(changed_file.filename, "tools/generate-compliance-tests/")
} else if {
	some changed_file in input
	changed_file.filename in build_change_files
} else if {
	some changed_file in input
	startswith(changed_file.filename, "gradle/")
}

changes["opa-jackson"] if {
	some changed_file in input
	startswith(changed_file.filename, "opa-jackson/")
} else if {
	some changed_file in input
	changed_file.filename in build_change_files
} else if {
	some changed_file in input
	startswith(changed_file.filename, "gradle/")
}

changes["opa-services"] if {
	some changed_file in input
	startswith(changed_file.filename, "opa-services/")
} else if {
	some changed_file in input
	changed_file.filename in build_change_files
} else if {
	some changed_file in input
	startswith(changed_file.filename, "gradle/")
}

changes["opa-builtins"] if {
	some changed_file in input
	startswith(changed_file.filename, "opa-builtins/")
} else if {
	some changed_file in input
	changed_file.filename in build_change_files
} else if {
	some changed_file in input
	startswith(changed_file.filename, "gradle/")
}

changes["opa-slf4j"] if {
	some changed_file in input
	startswith(changed_file.filename, "opa-slf4j/")
} else if {
	some changed_file in input
	changed_file.filename in build_change_files
} else if {
	some changed_file in input
	startswith(changed_file.filename, "gradle/")
}

changes["opa-proto"] if {
	some changed_file in input
	startswith(changed_file.filename, "opa-proto/")
} else if {
	# ProtoComplianceTest decodes the base64 plan_proto fixtures emitted by this generator, so a
	# change here (notably an OPA version bump) can alter the proto plans and must re-run its tests.
	some changed_file in input
	startswith(changed_file.filename, "tools/generate-compliance-tests/")
} else if {
	some changed_file in input
	changed_file.filename in build_change_files
} else if {
	some changed_file in input
	startswith(changed_file.filename, "gradle/")
}
