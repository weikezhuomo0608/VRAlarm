#!/usr/bin/env python3
"""Run the existing pure Java rule tests with a JDK or an explicitly supplied ECJ."""
import argparse
import pathlib
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ecj', type=pathlib.Path, help='Eclipse ECJ jar (optional)')
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[1]
    output = root / 'build' / 'core-tests'
    output.mkdir(parents=True, exist_ok=True)
    classes = ['TimeRules', 'LiveGate', 'AlertPolicy', 'WatchStatus', 'Anchors', 'PollPlan', 'Schedule']
    tests = ['CoreTests', 'AlertPolicyTests', 'WatchStatusTests', 'AnchorsTests', 'PollPlanTests', 'ScheduleTests']
    sources = [root / 'app/src/main/java/dev/hazel/livealarm' / (name + '.java')
               for name in classes]
    sources += [root / 'tests' / (name + '.java') for name in tests]
    compiler = (['java', '-jar', str(args.ecj.resolve()), '-1.8', '-warn:none']
                if args.ecj else ['javac', '-source', '8', '-target', '8'])
    subprocess.run(compiler + ['-encoding', 'UTF-8', '-d', str(output)]
                   + [str(source) for source in sources], check=True, cwd=root)
    for name in tests:
        subprocess.run(['java', '-cp', str(output), name], check=True, cwd=root)


if __name__ == '__main__':
    main()
