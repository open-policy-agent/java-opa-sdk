package rcx

inner := abs(input.x)

outer := inner + inner

nested := [outer, outer, outer]

by_name := [data.rcx.dyn[name] | some name in ["inner", "outer"]]

printed := v if {
	print("cached rule body")
	v := inner
}

with_print := [printed, printed, printed]

mut_base := {"k": {"n": 1}, "list": [1, 2]}

mut_grown := object.union(mut_base, {"z": 1})

mut_twice := [mut_base, mut_grown, mut_base, mut_grown]

mut_s contains x if some x in input.items

mut_s contains 99

mut_sets := [mut_s, mut_s, count(mut_s), mut_s]

mut_r.a[x] := 1 if some x in input.items

mut_r.a.b := 2

mut_r.c := mut_base

mut_refs := [mut_r, mut_r, mut_r.a, mut_r]

mut_o[k] := v if some k, v in input.map

mut_o.extra := {"deep": [1, 2]}

mut_objs := [mut_o, mut_o, mut_o.extra, mut_o]

mut_loop := [y | some x in input.items; y := {"x": x, "b": mut_base, "s": mut_s}]
