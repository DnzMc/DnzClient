package com.dnz.client.script;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/** A JavaScript function written in Java. Only plain values (text, numbers, true/false, functions) go in and out. */
final class JsFunction extends BaseFunction {
	interface Body {
		/** Return {@link #VOID} for "no result". */
		Object call(Context cx, Scriptable scope, Object[] args);
	}

	static final Object VOID = Undefined.instance;

	private final String name;
	private final int arity;
	private final Body body;

	JsFunction(Scriptable scope, String name, int arity, Body body) {
		super(scope, ScriptableObject.getFunctionPrototype(scope));
		this.name = name;
		this.arity = arity;
		this.body = body;
	}

	@Override
	public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
		return this.body.call(cx, scope, args);
	}

	@Override
	public String getFunctionName() {
		return this.name;
	}

	@Override
	public int getArity() {
		return this.arity;
	}

	@Override
	public int getLength() {
		return this.arity;
	}

	// ------------------------------------------------------------------ argument helpers

	static Object arg(Object[] args, int index) {
		return index < args.length ? args[index] : Undefined.instance;
	}

	static String text(Object[] args, int index) {
		Object value = arg(args, index);
		return value == null || Undefined.isUndefined(value) ? "" : Context.toString(value);
	}

	static double number(Object[] args, int index, double fallback) {
		Object value = arg(args, index);
		if (value == null || Undefined.isUndefined(value)) {
			return fallback;
		}
		double number = Context.toNumber(value);
		return Double.isNaN(number) ? fallback : number;
	}

	static org.mozilla.javascript.Function function(Object[] args, int index, String what) {
		Object value = arg(args, index);
		if (value instanceof org.mozilla.javascript.Function f) {
			return f;
		}
		throw Context.reportRuntimeError(what + ": a function is needed here, e.g. () => { ... }");
	}
}
