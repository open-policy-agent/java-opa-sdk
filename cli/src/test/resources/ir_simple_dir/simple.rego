package authz

default allow := false

allow if {
	input.user.id in {"kurt", "alice"}
}

allow if {
	data.groups[input.user.groups[_]].privileged
}
