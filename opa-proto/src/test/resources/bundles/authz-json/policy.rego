package authz

import rego.v1

default allow := false

allow if {
	input.method == "GET"
	some role in data.roles[input.user]
	role == "reader"
}

allow if user_is_admin

user_is_admin if "admin" in data.roles[input.user]

# array construction + numeric ops to exercise MakeArray/ArrayAppend/MakeNumber*/arithmetic
scores := [n |
	some rule in data.rules
	rule.user == input.user
	n := count(rule.method) + 1
]

summary := {
	"user": input.user,
	"admin": user_is_admin,
	"scores": scores,
	"total": sum(scores),
	"rule_count": count(data.rules),
}
