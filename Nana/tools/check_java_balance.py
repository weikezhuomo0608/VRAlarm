#!/usr/bin/env python3
"""Structural check for hand-edited Java: comment/string aware delimiter balance.

Not a compiler. It catches the mechanical damage a large edit can do (unbalanced
braces or parentheses), which is otherwise only visible from a real javac run.
"""
import pathlib
import sys


def scan(text):
    """Yield (line, column, char) for code characters only."""
    line = 1
    column = 0
    i = 0
    length = len(text)
    while i < length:
        c = text[i]
        column += 1
        if c == '\n':
            line += 1
            column = 0
            i += 1
            continue
        if c == '/' and i + 1 < length and text[i + 1] == '/':
            while i < length and text[i] != '\n':
                i += 1
            continue
        if c == '/' and i + 1 < length and text[i + 1] == '*':
            i += 2
            while i + 1 < length and not (text[i] == '*' and text[i + 1] == '/'):
                if text[i] == '\n':
                    line += 1
                    column = 0
                i += 1
            i += 2
            continue
        if c == '"':
            i += 1
            while i < length:
                if text[i] == '\\':
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                if text[i] == '\n':
                    line += 1
                    column = 0
                i += 1
            continue
        if c == "'":
            i += 1
            while i < length:
                if text[i] == '\\':
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        yield line, column, c
        i += 1


def check(path):
    text = path.read_text(encoding='utf-8')
    pairs = {')': '(', '}': '{', ']': '['}
    opens = set(pairs.values())
    stack = []
    for line, column, c in scan(text):
        if c in opens:
            stack.append((c, line, column))
        elif c in pairs:
            if not stack:
                return '%s:%d:%d unexpected %s' % (path.name, line, column, c)
            top, oline, ocolumn = stack.pop()
            if top != pairs[c]:
                return ('%s:%d:%d %s closes %s opened at line %d'
                        % (path.name, line, column, c, top, oline))
    if stack:
        top, oline, ocolumn = stack[-1]
        return '%s unclosed %s opened at line %d' % (path.name, top, oline)
    return None


def main():
    root = pathlib.Path(__file__).resolve().parents[1]
    targets = []
    for pattern in ('app/src/main/java/**/*.java', 'tests/*.java'):
        targets.extend(sorted(root.glob(pattern)))
    failed = 0
    for path in targets:
        problem = check(path)
        if problem:
            print('FAIL ' + problem)
            failed += 1
    print('%d files checked, %d unbalanced' % (len(targets), failed))
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
