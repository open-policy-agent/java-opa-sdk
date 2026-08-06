# Mask policy whose evaluation fails: regex.match lives in opa-builtins-regex, which is not on the
# opa-services classpath. A separate plan because a missing builtin invalidates every entrypoint.
#
#   opa build -t plan -e test/log/mask_error -o bundle.tar.gz errormask.rego \
#     && tar xzf bundle.tar.gz -O /plan.json > plan-error.json
package test.log

import rego.v1

mask_error contains "/input/password" if {
	regex.match("^secret", input.input.password)
}
