import { describe, expect, it } from 'vitest'
import { formatJava } from './formatJava'

describe('formatJava', () => {
  it('re-indents by brace depth', () => {
    const input = [
      'public class Main {',
      'public static void main(String[] args) {',
      '      int x = 1;',
      'if (x > 0) {',
      'System.out.println(x);',
      '} else {',
      'x--;',
      '}',
      '}',
      '}',
    ].join('\n')

    expect(formatJava(input)).toBe(
      [
        'public class Main {',
        '    public static void main(String[] args) {',
        '        int x = 1;',
        '        if (x > 0) {',
        '            System.out.println(x);',
        '        } else {',
        '            x--;',
        '        }',
        '    }',
        '}',
        '',
      ].join('\n'),
    )
  })

  it('ignores braces in strings, chars and comments', () => {
    const input = [
      'class A {',
      'String s = "{ not a block";',
      "char c = '}';",
      '// } comment',
      '/* { */',
      'int y;',
      '}',
    ].join('\n')

    expect(formatJava(input)).toBe(
      [
        'class A {',
        '    String s = "{ not a block";',
        "    char c = '}';",
        '    // } comment',
        '    /* { */',
        '    int y;',
        '}',
        '',
      ].join('\n'),
    )
  })

  it('collapses blank runs, trims trailing space, normalizes CRLF', () => {
    expect(formatJava('class A {   \r\n\r\n\r\nint x;\r\n}\r\n\r\n')).toBe(
      'class A {\n\n    int x;\n}\n',
    )
  })

  it('is idempotent', () => {
    const once = formatJava('class A {\nvoid f() {\nint[] a = {1, 2};\n}\n}')
    expect(formatJava(once)).toBe(once)
  })

  it('aligns block comment continuation lines', () => {
    expect(formatJava('class A {\n/**\n* doc\n*/\nint x;\n}')).toBe(
      'class A {\n    /**\n     * doc\n     */\n    int x;\n}\n',
    )
  })
})
