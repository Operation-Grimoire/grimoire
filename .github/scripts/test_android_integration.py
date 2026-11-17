import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("android_integration", Path(__file__).with_name("android_integration.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


def events():
    result = []
    for name in runner.TESTS:
        for code in (1, 0):
            result.extend([f"INSTRUMENTATION_STATUS: class={runner.CLASS}", f"INSTRUMENTATION_STATUS: test={name}",
                           f"INSTRUMENTATION_STATUS: numtests={len(runner.TESTS)}", f"INSTRUMENTATION_STATUS_CODE: {code}"])
    return "\n".join(result) + f"\nOK ({len(runner.TESTS)} tests)\nINSTRUMENTATION_CODE: -1\n"


class IntegrationResultTest(unittest.TestCase):
    def test_complete_exact_inventory(self):
        self.assertEqual(sorted(runner.TESTS), runner.parse_instrumentation(events()))

    def test_zero_and_partial_inventory_fail(self):
        for output in ["OK (3 tests)\nINSTRUMENTATION_CODE: -1", events().replace(runner.TESTS[0], "missing")]:
            with self.assertRaises(ValueError):
                runner.parse_instrumentation(output)

    def test_skips_assumptions_failures_and_ignored_tests_fail(self):
        for code in [-1, -2, -3, -4, 2]:
            with self.assertRaises(ValueError):
                runner.parse_instrumentation(events().replace("INSTRUMENTATION_STATUS_CODE: 0", f"INSTRUMENTATION_STATUS_CODE: {code}", 1))

    def test_duplicate_or_unpaired_completion_fails(self):
        for output in [events().replace("INSTRUMENTATION_STATUS_CODE: 1", "INSTRUMENTATION_STATUS_CODE: 0", 1),
                       events() + events()]:
            with self.assertRaises(ValueError):
                runner.parse_instrumentation(output)

    def test_wrong_class_count_or_incomplete_finish_fails(self):
        for output in [events().replace(runner.CLASS, "another.Class"), events().replace("numtests=3", "numtests=4"),
                       events().replace("INSTRUMENTATION_CODE: -1", ""), events().replace("OK (3 tests)", "")]:
            with self.assertRaises(ValueError):
                runner.parse_instrumentation(output)


if __name__ == "__main__":
    unittest.main()
