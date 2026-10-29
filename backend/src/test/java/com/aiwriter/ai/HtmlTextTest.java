package com.aiwriter.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 纯文本 ↔ HTML。
 *
 * <p>锁定的回归：AI 生成的章节是纯文本，直接塞给 Tiptap 会折叠所有换行，
 * 在编辑器里看就是「一点排版都没有」。入库前要转成 &lt;p&gt; 段落。
 */
class HtmlTextTest {

    @Test
    void plainTextBecomesParagraphs() {
        String plain = "第一段。\n\n第二段，第一行。\n第二行。\n\n第三段。";
        assertEquals("<p>第一段。</p><p>第二段，第一行。<br>第二行。</p><p>第三段。</p>",
                HtmlText.plainToHtml(plain));
    }

    @Test
    void shouldEscapeHtmlInModelOutput() {
        String html = HtmlText.plainToHtml("他写下 <script>alert(1)</script> & \"引号\"");
        assertTrue(html.contains("&lt;script&gt;"), "标签必须被转义");
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&amp;"));
    }

    /** 模型开头的 "## 第一章" 要变成真标题，不能在编辑器里显示井号。 */
    @Test
    void markdownHeadingBecomesHeadingTag() {
        assertEquals("<h2>第一章 雾渡</h2><p>雾还没散。</p>",
                HtmlText.plainToHtml("## 第一章 雾渡\n\n雾还没散。"));
    }

    @Test
    void htmlToPlainRestoresParagraphs() {
        String html = "<p>第一段。</p><p>第二段，第一行。<br>第二行。</p>";
        assertEquals("第一段。\n\n第二段，第一行。\n第二行。", HtmlText.htmlToPlain(html));
    }

    @Test
    void roundTripIsLosslessForGeneratedContent() {
        String plain = "雾还没散。\n\n他站在渡口。\n\n船来了。";
        assertEquals(plain, HtmlText.htmlToPlain(HtmlText.plainToHtml(plain)));
    }

    @Test
    void ensureHtmlLeavesRealHtmlAlone() {
        String html = "<h2>标题</h2><p>正文</p>";
        assertEquals(html, HtmlText.ensureHtml(html));
        assertEquals("<p>纯文本</p>", HtmlText.ensureHtml("纯文本"));
        assertEquals("", HtmlText.ensureHtml(""));
        assertEquals("", HtmlText.ensureHtml(null));
    }

    @Test
    void looksLikeHtmlOnlyForBlockTags() {
        assertTrue(HtmlText.looksLikeHtml("<p>x</p>"));
        assertTrue(HtmlText.looksLikeHtml("<h2>x</h2>"));
        assertFalse(HtmlText.looksLikeHtml("普通一句话"));
        assertFalse(HtmlText.looksLikeHtml("有 < 号但没标签"));
    }
}
