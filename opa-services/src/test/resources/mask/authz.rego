# Decision policy for OpaDecisionLogMaskTest. Regenerate plan-authz.json with:
#
#   opa build -t plan -e authz/decision -e authz/allow -o bundle.tar.gz authz.rego \
#     && tar xzf bundle.tar.gz -O /plan.json > plan-authz.json
package authz

import rego.v1

decision := {"allow": input.user == "alice", "token": input.token}

allow if input.user == "alice"
