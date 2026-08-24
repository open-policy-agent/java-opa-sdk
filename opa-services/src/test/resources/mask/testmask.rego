package test.log

import rego.v1

# Path outside input/result/nd_builtin_cache: rejected while parsing the rule set.
mask_invalid contains "/labels/environment"

# Two rules for one path, to pin down what conflicting rules do. OPA orders a set's elements by
# type and then value, so the shorthand remove comes ahead of the upsert and the upsert is the one
# that survives. This SDK's evaluator yields a set in rule order, which is the same order here.
mask_conflict contains "/input/password" if {
	input.input.password
}

mask_conflict contains {"op": "upsert", "path": "/input/password", "value": "**REDACTED**"} if {
	input.input.password
}

# A complete rule returns an array, which fixes the order: here the upsert runs before the remove,
# so the field ends up gone rather than redacted.
mask_order := [
	{"op": "upsert", "path": "/result/token", "value": "**REDACTED**"},
	"/result/token",
]
