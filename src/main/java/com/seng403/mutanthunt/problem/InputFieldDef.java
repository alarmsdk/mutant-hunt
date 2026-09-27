package com.seng403.mutanthunt.problem;

/**
 * Describes an input field shown to the student.
 *
 * @param id         field identifier used in raw-input maps
 * @param label      message-key for the label (resolved via MessageSource)
 * @param placeholder message-key for the placeholder
 * @param inputMode  HTML inputmode attribute ("numeric", "text", …)
 */
public record InputFieldDef(String id, String label, String placeholder, String inputMode) {
}
