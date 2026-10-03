package com.dnz.client.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

/**
 * Checks the DNZ Script sandbox without starting Minecraft (gradlew scriptCheck):
 * no Java access, endless loops are stopped, errors have the right line, and which JavaScript features work.
 */
public final class ScriptEngineCheck {
	private static int failures;

	public static void main(String[] args) {
		System.out.println("== Sandbox");
		expectError("java.lang.System.exit(0)", "java access");
		expectError("Packages.java.io.File", "Packages");
		expectError("new java.io.File('x')", "new java.io.File");
		expectError("(function(){ return this; })().getClass()", "getClass");
		expectError("importPackage(java.io)", "importPackage");

		System.out.println("== Time limit");
		expectTimeout("while (true) {}");
		expectTimeout("try { while (true) {} } catch (e) {} finally { while (true) {} }");
		expectError("function f() { return f(); } f();", "deep recursion");

		System.out.println("== Error lines");
		try {
			eval("let a = 1;\nlet b = 2;\nundefinedFunction();\n");
			fail("error lines: no error");
		} catch (RhinoException e) {
			check("error on line 3", e.lineNumber() == 3, "line " + e.lineNumber() + ": " + e.details());
		}

		System.out.println("== JavaScript features (for the guide)");
		feature("let / const", "let a = 1; const b = 2; a + b", "3");
		feature("arrow functions", "[1, 2, 3].map(x => x * 2).join(',')", "2,4,6");
		feature("template strings", "let hp = 5; `Can: ${hp}`", "Can: 5");
		feature("default parameters", "function f(a, b = 2) { return a + b; } f(1)", "3");
		feature("destructuring", "const { x, y } = { x: 1, y: 2 }; x + y", "3");
		feature("spread", "Math.max(...[1, 5, 3])", "5");
		feature("for...of", "let s = 0; for (const n of [1, 2, 3]) s += n; s", "6");
		feature("Map / Set", "new Map([[1, 'a']]).get(1) + new Set([1, 1, 2]).size", "a2");
		feature("classes", "class A { f() { return 7; } } new A().f()", "7");
		feature("JSON", "JSON.stringify({ a: [1, 2] })", "{\"a\":[1,2]}");
		feature("Date", "typeof new Date().getHours()", "number");

		System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static Object eval(String code) {
		return ScriptEngine.run(ScriptEngine.CALL_BUDGET_MS, cx -> {
			Scriptable scope = cx.initSafeStandardObjects();
			Object result = cx.evaluateString(scope, code, "check.js", 1, null);
			return Context.toString(result);
		});
	}

	private static void expectError(String code, String name) {
		try {
			Object result = eval(code);
			fail(name + ": ran without error (" + result + ")");
		} catch (ScriptEngine.Timeout e) {
			fail(name + ": timed out instead of an error");
		} catch (RhinoException e) {
			System.out.println("  ok  " + name + " -> blocked: " + e.details());
		} catch (RuntimeException | StackOverflowError e) {
			// Blocked, but not as a script error; still safe, report it.
			System.out.println("  ok  " + name + " -> blocked (" + e.getClass().getSimpleName() + "): " + e.getMessage());
		}
	}

	private static void expectTimeout(String code) {
		long start = System.nanoTime();
		try {
			eval(code);
			fail("timeout: \"" + code + "\" finished");
		} catch (ScriptEngine.Timeout e) {
			long ms = (System.nanoTime() - start) / 1_000_000;
			check("stopped \"" + code + "\" after " + ms + " ms", ms < 500, "");
		} catch (RuntimeException e) {
			fail("timeout: \"" + code + "\" gave " + e);
		}
	}

	private static void feature(String name, String code, String expected) {
		try {
			Object result = eval(code);
			System.out.println("  " + (expected.equals(result) ? "yes " : "odd ") + name + (expected.equals(result) ? "" : " -> " + result));
		} catch (RuntimeException e) {
			System.out.println("  no  " + name + " (" + e.getMessage() + ")");
		}
	}

	private static void check(String name, boolean ok, String detail) {
		if (ok) {
			System.out.println("  ok  " + name);
		} else {
			fail(name + " " + detail);
		}
	}

	private static void fail(String message) {
		failures++;
		System.out.println("  FAIL " + message);
	}
}
