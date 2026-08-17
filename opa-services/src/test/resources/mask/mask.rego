# Mask policies for DecisionLogPluginMaskTest. Regenerate plan.json with:
#
#   opa build -t plan -e system/log/mask -e test/log/mask_invalid \
#     -e test/log/mask_conflict -e test/log/mask_order \
#     -o bundle.tar.gz mask.rego testmask.rego && tar xzf bundle.tar.gz -O /plan.json > plan.json
package system.log

import rego.v1

# Shorthand form.
mask contains "/input/password" if {
	input.input.password
}

# Structured form.
mask contains {"op": "upsert", "path": "/result/token", "value": "**REDACTED**"} if {
	input.result.token
}
