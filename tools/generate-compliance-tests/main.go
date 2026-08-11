package main

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"strings"

	cases "github.com/open-policy-agent/opa/build/generate-extended-cases"
	"github.com/open-policy-agent/opa/v1/ir"
	"google.golang.org/protobuf/proto"
	//"github.com/open-policy-agent/opa/v1/ast"
)

// caseWithProto augments each generated case with the protobuf wire-form of its
// plan (base64-encoded), alongside the existing JSON plan. Embedding the
// upstream case means every original field is emitted verbatim, in order, with
// "plan_proto" appended last — so the proto column can be added to the fixtures
// without perturbing the JSON that the JSON-path compliance test already reads.
// The ProtoComplianceTest in opa-proto decodes this field, giving the proto
// decoder the same coverage as the JSON reader across the whole suite.
type caseWithProto struct {
	*cases.ExtendedTestCase
	PlanProto string `json:"plan_proto"`
}

type setWithProto struct {
	Cases []caseWithProto `json:"cases"`
}

func main() {
	//if len(os.Args) < 3 {
	//	fmt.Println("Usage: main <output-dir> <capabilities-file>")
	//	os.Exit(1)
	//}
	//
	outputDir := os.Args[1]
	//capabilitiesFile := os.Args[2]
	//
	//c, err := ast.LoadCapabilitiesFile(capabilitiesFile)
	//if err != nil {
	//	fmt.Println(err)
	//	os.Exit(1)
	//}

	//extendedSets, err := cases.LoadIrExtendedTestCasesFiltered(cases.CapabilitiesFilter(c))
	//if err != nil {
	//	panic(err)
	//}

	extendedSets, err := cases.LoadIrExtendedTestCasesFiltered()
	if err != nil {
		panic(err)
	}

	for _, extendedSet := range extendedSets {
		// Cases excluded via exceptions.yaml are left in the set by the loader
		// without a Filename or Plan. Keep only prepared cases so excluded ones
		// are not written out (and so we don't index an empty Filename below).
		var prepared []*cases.ExtendedTestCase
		for _, tc := range extendedSet.Cases {
			if tc.Filename != "" {
				prepared = append(prepared, tc)
			}
		}
		extendedSet.Cases = prepared
		if len(extendedSet.Cases) == 0 {
			continue
		}

		// Encode each plan to its protobuf wire-form using OPA's own IR->proto
		// converter (the same path as `opa build --format=proto`), so the proto
		// fixtures are faithful to OPA rather than a Java-side re-encoding.
		out := setWithProto{Cases: make([]caseWithProto, len(extendedSet.Cases))}
		for i, tc := range extendedSet.Cases {
			pbPolicy, perr := ir.PolicyToProto(tc.Plan)
			if perr != nil {
				panic(fmt.Errorf("Failed to convert plan to proto for %s: %w", tc.Filename, perr))
			}
			bs, merr := proto.MarshalOptions{Deterministic: true}.Marshal(pbPolicy)
			if merr != nil {
				panic(fmt.Errorf("Failed to marshal proto plan for %s: %w", tc.Filename, merr))
			}
			out.Cases[i] = caseWithProto{
				ExtendedTestCase: tc,
				PlanProto:        base64.StdEncoding.EncodeToString(bs),
			}
		}

		tcJson, err := json.MarshalIndent(out, "", "\t")
		if err != nil {
			panic(fmt.Errorf("Failed to marshal test case to json: %s\n", err.Error()))
		}

		tPath := strings.Split(extendedSet.Cases[0].Filename, "/")
		folderPath := fmt.Sprintf("%s/%s", outputDir, tPath[len(tPath)-2])
		tcFileName := strings.ReplaceAll(tPath[len(tPath)-1], ".yaml", ".json")

		if err := os.MkdirAll(folderPath, 0755); err != nil {
			panic(err)
		}

		if err := os.WriteFile(fmt.Sprintf("%s/%s", folderPath, tcFileName), tcJson, 0644); err != nil {
			panic(fmt.Errorf("Failed to write test case: %s\n", err.Error()))
		}
	}
}
