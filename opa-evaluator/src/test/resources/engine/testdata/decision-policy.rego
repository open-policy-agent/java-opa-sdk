# This policy returns a structured object decision rather than a primitive,
# so tests can exercise Engine.PreparedQuery#eval(input, Class<T>) with a POJO
# result type.
#
# To regenerate the IR plan, compile with:
#   opa build -t plan -e authz/decision -o bundle.tar.gz decision-policy.rego
# Then extract /plan.json from the bundle tarball.

package authz

decision := {
	"allowed": allowed,
	"user_id": input.user.id,
	"reason": reason,
}

default allowed := false

allowed if {
	input.user.id == "alice"
}

default reason := "denied"

reason := "matched-user-id" if {
	input.user.id == "alice"
}
