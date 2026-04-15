// phantasia-core/src/main/java/com/phantasia/core/model/DialogueTextRenderer.java
package com.phantasia.core.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles token substitution and conditional text in dialogue strings.
 *
 * Harvested from the Fragment system's TextRenderer and simplified to
 * work with {@link DialogueContext} instead of NpcContextSnapshot.
 *
 * TOKENS:
 *   {player_name}, {npc_name}, {gold}, {party_size}
 *   Replaced with values from DialogueContext.tokens().
 *   Unrecognized tokens pass through unchanged: {unknown} → {unknown}
 *
 * CONDITIONALS:
 *   {if gold >= 100}You're wealthy!{else}Times are tough.{endif}
 *
 *   Supported operators: >, <, >=, <=, ==, !=
 *   Left side must be a property name from DialogueContext.properties().
 *   Right side is a numeric literal.
 *   The {else} branch is optional.
 *
 * PROCESSING ORDER:
 *   1. Conditional blocks are evaluated first.
 *   2. Token substitution runs on the surviving text.
 *   This prevents tokens inside an unchosen branch from being substituted.
 *
 * USAGE:
 *   DialogueTextRenderer renderer = new DialogueTextRenderer();
 *   String result = renderer.render(
 *       "Hello, {player_name}! {if gold >= 100}Rich!{else}Poor!{endif}",
 *       context);
 */
public class DialogueTextRenderer {

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\\{(\\w+)}");

    private static final Pattern IF_PATTERN =
            Pattern.compile(
                    "\\{if\\s+(.*?)}(.*?)(?:\\{else}(.*?))?\\{endif}",
                    Pattern.DOTALL);

    /**
     * Renders a dialogue string by evaluating conditionals and substituting tokens.
     *
     * @param template the raw dialogue text with tokens and conditionals
     * @param context  the frozen game state snapshot
     * @return the fully rendered string
     */
    public String render(String template, DialogueContext context) {
        if (template == null || template.isEmpty()) return "";

        // 1. Evaluate conditional blocks first
        String result = processConditionals(template, context);

        // 2. Substitute remaining tokens
        return replaceTokens(result, context);
    }

    // -------------------------------------------------------------------------
    // Token substitution
    // -------------------------------------------------------------------------

    private String replaceTokens(String text, DialogueContext context) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = TOKEN_PATTERN.matcher(text);

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = context.getToken(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Conditional blocks
    // -------------------------------------------------------------------------

    private String processConditionals(String text, DialogueContext context) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = IF_PATTERN.matcher(text);

        while (matcher.find()) {
            String expression  = matcher.group(1);
            String ifContent   = matcher.group(2);
            String elseContent = matcher.group(3) != null ? matcher.group(3) : "";

            String replacement = evaluateCondition(expression, context)
                    ? ifContent : elseContent;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Expression evaluation
    // -------------------------------------------------------------------------

    private static final Pattern EXPR_PATTERN =
            Pattern.compile("(\\w+)\\s*(>=|<=|==|!=|>|<)\\s*(.+)");

    /**
     * Evaluates a simple comparison expression: "property op value".
     * Only numeric comparisons are supported — the left side must be a
     * property name, the right side a numeric literal.
     */
    private boolean evaluateCondition(String expression, DialogueContext context) {
        Matcher m = EXPR_PATTERN.matcher(expression.trim());
        if (!m.matches()) return false;

        String leftKey  = m.group(1);
        String operator = m.group(2);
        String rightStr = m.group(3).trim();

        Number leftNum = context.getProperty(leftKey);
        if (leftNum == null) return false;

        try {
            float left  = leftNum.floatValue();
            float right = Float.parseFloat(rightStr);

            return switch (operator) {
                case ">"  -> left >  right;
                case "<"  -> left <  right;
                case ">=" -> left >= right;
                case "<=" -> left <= right;
                case "==" -> left == right;
                case "!=" -> left != right;
                default   -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }
}