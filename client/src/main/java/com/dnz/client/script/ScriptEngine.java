package com.dnz.client.script;

import java.util.function.Function;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;

/**
 * Rhino (JavaScript) set up for DNZ Script mods:
 * <ul>
 *   <li>no Java access at all (every Java class is hidden from scripts, no Packages/java objects),</li>
 *   <li>a time limit per call, so an endless loop stops instead of freezing the game,</li>
 *   <li>modern syntax (let/const, arrow functions, template strings).</li>
 * </ul>
 * All script code runs on the game (render) thread.
 */
final class ScriptEngine {
	/** Loading a mod (running its top-level code). */
	static final long LOAD_BUDGET_MS = 1000;
	/** One event, timer, key or command handler. */
	static final long CALL_BUDGET_MS = 40;
	/** One HUD value (runs every few frames). */
	static final long HUD_BUDGET_MS = 5;

	private static final Factory FACTORY = new Factory();

	private ScriptEngine() {
	}

	/** Thrown when a script runs too long. It is an Error so script code can't catch it. */
	static final class Timeout extends Error {
		Timeout() {
			super("time limit", null, false, false);
		}
	}

	private static final class BudgetContext extends Context {
		long deadline;

		BudgetContext(ContextFactory factory) {
			super(factory);
		}
	}

	private static final class Factory extends ContextFactory {
		@Override
		protected Context makeContext() {
			BudgetContext cx = new BudgetContext(this);
			cx.setLanguageVersion(Context.VERSION_ES6);
			cx.setInterpretedMode(true); // needed for instruction counting (time limit)
			cx.setInstructionObserverThreshold(10_000);
			cx.setMaximumInterpreterStackDepth(400);
			cx.setClassShutter(className -> false); // scripts can't see any Java class
			return cx;
		}

		@Override
		protected void observeInstructionCount(Context cx, int instructionCount) {
			if (cx instanceof BudgetContext budget && budget.deadline != 0 && System.nanoTime() > budget.deadline) {
				throw new Timeout();
			}
		}
	}

	/** Runs [action] in a script context that stops after [budgetMs]. */
	static <T> T run(long budgetMs, Function<Context, T> action) {
		Context cx = FACTORY.enterContext();
		try {
			BudgetContext budget = (BudgetContext) cx;
			long previous = budget.deadline;
			budget.deadline = System.nanoTime() + budgetMs * 1_000_000L;
			try {
				return action.apply(cx);
			} finally {
				budget.deadline = previous;
			}
		} finally {
			Context.exit();
		}
	}
}
