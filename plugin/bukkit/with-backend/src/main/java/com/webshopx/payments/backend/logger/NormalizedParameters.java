package com.webshopx.payments.backend.logger;

/**
 * Holds normalized call parameters.
 * <p>
 * Includes utility methods such as {@link #normalize(String, Object[], Throwable)} to help the normalization of parameters.
 *
 * @author ceki
 * @since 2.0
 */
public class NormalizedParameters {

    final String message;
    final Object[] arguments;
    final Throwable throwable;

    public NormalizedParameters(String message, Object[] arguments, Throwable throwable) {
        this.message = message;
        this.arguments = arguments;
        this.throwable = throwable;
    }

    public NormalizedParameters(String message, Object[] arguments) {
        this(message, arguments, null);
    }

    public String getMessage() {
        return message;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * Helper method to get all but the last element of an array
     *
     * @param argArray The arguments from which we want to remove the last element
     *
     * @return a copy of the array without the last element
     */
    public static Object[] trimmedCopy(final Object[] argArray) {
        if (argArray == null || argArray.length == 0) {
            throw new IllegalStateException("non-sensical empty or null argument array");
        }

        final int trimmedLen = argArray.length - 1;

        Object[] trimmed = new Object[trimmedLen];

        if (trimmedLen > 0) {
            System.arraycopy(argArray, 0, trimmed, 0, trimmedLen);
        }

        return trimmed;
    }

    /**
     * This method serves to normalize logging call invocation parameters.
     * <p>
     * More specifically, if a throwable argument is not supplied directly, it
     * attempts to extract it from the argument array.
     */
    public static NormalizedParameters normalize(String msg, Object[] arguments, Throwable t) {

        if (t != null) {
            return new NormalizedParameters(msg, arguments, t);
        }

        if (arguments == null || arguments.length == 0) {
            return new NormalizedParameters(msg, arguments, null);
        }

        Throwable throwableCandidate = org.slf4j.helpers.NormalizedParameters.getThrowableCandidate(arguments);
        if (throwableCandidate != null) {
            Object[] trimmedArguments = trimmedCopy(arguments);
            return new NormalizedParameters(msg, trimmedArguments, throwableCandidate);
        } else {
            return new NormalizedParameters(msg, arguments);
        }

    }
}
