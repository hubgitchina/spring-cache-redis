package cn.com.ut.cache.expression;

import org.springframework.expression.EvaluationException;

/**
 * A specific {@link EvaluationException} to mention that a given variable used
 * in the expression is not available in the context.
 *
 * @author Stephane Nicoll
 * @since 4.0.6
 */
@SuppressWarnings("serial")
class VariableNotAvailableException extends EvaluationException {

	private final String name;

	public VariableNotAvailableException(String name) {
		super("Variable '" + name + "' is not available");
		this.name = name;
	}

	public String getName() {

		return this.name;
	}
}
