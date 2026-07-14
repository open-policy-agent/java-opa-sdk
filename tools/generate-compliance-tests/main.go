package main

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"

	cases "github.com/open-policy-agent/opa/build/generate-extended-cases"
	//"github.com/open-policy-agent/opa/v1/ast"
)

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

		tcJson, err := json.MarshalIndent(extendedSet, "", "\t")
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
