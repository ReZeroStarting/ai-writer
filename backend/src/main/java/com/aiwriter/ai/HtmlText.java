package com.aiwriter.ai;

import java.util.regex.Pattern;

/**
 * 纯文本与 HTML 之间的转换。
 *
 * <p>为什么需要：文档表里既存「编辑器写的 HTML」，也存「AI 流式生成的纯文本」
 * （框架写作）。编辑器（Tiptap）只认 HTML，一段没有标签的纯文本丢进去，
 * 换行会被折叠，看起来就是「一点排版都没有」。
 *
 * <p>约定：
 * <ul>
 *   <li>入库统一存 HTML（编辑器直接能渲染，导出也不丢段落）；</li>
 *   <li>喂给模型 / 显示在框架画布上时转回纯文本；</li>
 *   <li>框架章节的 HTML 由纯文本生成（只有 {@code <p>} / {@code <br>}），
 *       所以 plain → html → plain 是无损的。</li>
 * </ul>
 */
public final class HtmlText {

    /** 粗判一段文本是不是 HTML：包含段落/换行/标题这类块级标签才算。 */
    private static final Pattern BLOCK_TAG =
            Pattern.compile("<\\s*(p|br|div|h[1-6]|blockquote|ul|ol|li)\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    private HtmlText() {
    }

    public static boolean looksLikeHtml(String text) {
        return text != null && BLOCK_TAG.matcher(text).find();
    }

    /** 纯文本 → 段落化 HTML：空行分段，段内换行变 &lt;br&gt;，特殊字符转义。 */
    public static String plainToHtml(String plain) {
        if (plain == null || plain.isBlank()) return "";
        String normalized = plain.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder html = new StringBuilder(normalized.length() + 32);
        for (String block : normalized.split("\n{2,}")) {
            String text = block.strip();
            if (text.isEmpty()) continue;
            // 模型常以 markdown 标题开头（"## 第一章 雾渡"），转成真正的标题，别让它显示成井号
            if (text.matches("^#{1,6}\\s+.*") && !text.contains("\n")) {
                html.append("<h2>").append(escape(text.replaceFirst("^#{1,6}\\s+", ""))).append("</h2>");
                continue;
            }
            html.append("<p>").append(escape(text).replace("\n", "<br>")).append("</p>");
        }
        return html.toString();
    }

    /** 已经是 HTML 就原样返回，否则按纯文本段落化。 */
    public static String ensureHtml(String content) {
        if (content == null || content.isBlank()) return "";
        return looksLikeHtml(content) ? content : plainToHtml(content);
    }

    /** HTML → 纯文本：块级元素变段落分隔，行内标签直接去掉，实体还原。 */
    public static String htmlToPlain(String html) {
        if (html == null) return "";
        if (!looksLikeHtml(html)) return html;
        String text = html
                .replaceAll("(?i)</p\\s*>", "\n\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(div|h[1-6]|li|blockquote)\\s*>", "\n")
                .replaceAll("<[^>]*>", "");
        text = unescape(text);
        return text.replaceAll("[ \\t]+\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static String unescape(String text) {
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
    }
}
