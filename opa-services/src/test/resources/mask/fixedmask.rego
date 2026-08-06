# Working stand-in for errormask.rego's entrypoint, to check that masking recovers once a good
# bundle is activated. Regenerate plan-fixed.json with:
#
#   opa build -t plan -e test/log/mask_error -o bundle.tar.gz fixedmask.rego \
#     && tar xzf bundle.tar.gz -O /plan.json > plan-fixed.json
package test.log

import rego.v1

mask_error contains "/input/password" if {
	input.input.password
}
