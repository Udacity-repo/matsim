package playground.joel.html;

import playground.clruch.utils.GlobalAssert;

import java.io.*;

/**
 * Created by Joel on 26.06.2017.
 */
public abstract class htmlUtils {
    public static StringBuilder stringBuilder = new StringBuilder();
    public static boolean html = false;
    public static boolean head = false;
    public static boolean header = false;
    public static boolean body = false;
    public static boolean paragraph = false;
    public static boolean footer = false;
    public static boolean style = false;

    /**
     * called at the begin and end of document
     */
    public static void html() {
        if (html) {stringBuilder.append("</html>"); html = false;}
        else {
            stringBuilder.append("<html>"); html = true;
            htmlUtils.insertCSS("header, footer {font-family: arial;", //
                    "background-color: #000099;", //
                    "color: white;", //
                    "padding: 20px;}", //
                    "body {font-family: times;", //
                    "line-height: 1.5}");
        }
    }

    /**
     * called at the begin and end of head
     */
    public static void head() {
        if (head) {stringBuilder.append("</head>"); head = false;}
        else {stringBuilder.append("<head>"); head = true;}
    }

    /**
     * called at the begin and end of header
     */
    public static void header() {
        if (header) {stringBuilder.append("</header>"); header = false;}
        else {stringBuilder.append("<header>"); header = true;}
    }

    /**
     * called at the begin and end of document
     */
    public static void body() {
        if (body) {stringBuilder.append("</body>"); body = false;}
        else {stringBuilder.append("<body>"); body = true;}
    }

    /**
     * called at the begin and end of paragraph
     */
    public static void paragraph() {
        if (paragraph) {stringBuilder.append("</p>"); paragraph = false;}
        else {stringBuilder.append("<p>"); paragraph = true;}
    }

    public static void newLine() {
        stringBuilder.append("<br style=\"clear:both\">");
    }

    /**
     * called at the begin and end of footer
     */
    public static void footer() {
        if (footer) {stringBuilder.append("</footer>"); header = false;}
        else {stringBuilder.append("<footer>"); footer = true;}
    }

    /**
     * called at the begin and end of CSS
     */
    public static void style() {
        if (style) {stringBuilder.append("</style>"); style = false;}
        else {stringBuilder.append("<style>"); style = true;}
    }

    /**
     * tab title
     *
     * @param title
     */
    public static void setTitle(String title) {
        head();
        stringBuilder.append("<title>" + title + "</title>");
        head();
    }

    /**
     * page title
     *
     * @param title
     */
    public static void insertTitle(String title) {
        header();
        stringBuilder.append("<h1>" + title + "</h1>");
        header();
    }

    public static void title(String title) {
        insertTitle(title);
        setTitle(title);
    }

    public static void insertText(String... lines) {
        paragraph();
        for (int i = 0; i < lines.length; i++)
            stringBuilder.append(lines[i] + "<br>");
        paragraph();
    }

    public static void insertText(String text) {
        stringBuilder.append("<pre>" + text + "</pre>");
    }

    public static void insertLink(String url, String link) {
        htmlUtils.insertCSS("a {font-family: arial;}");
        if (footer || header) htmlUtils.insertCSS("a {color: white;}");
        stringBuilder.append("<a href=\"" + url + "\"> <b>" + link + "</b></a>");
    }

    public static void insertCSS(String... line) {
        head();
        style();
        for (int i = 0; i < line.length; i++)
            stringBuilder.append(line[i]);
        style();
        head();
    }

    public static void insertImg(String relPath) {
        insertImg(relPath, 800, 600);
    }

    public static void insertImg(String relPath, int width, int heigth) {
        stringBuilder.append("<img src=" + relPath + " alt=\"image not found\" style=\"width:" + //
                width + "px;height:" + heigth + "px;\">");
    }

    /**
     * first elements are automatically set as headings
     *
     * @param columns
     * @param rows
     * @param borders
     * @param content
     */
    public static void insertTable(int columns, int rows, boolean borders, String... content) {
        if (content.length != columns*rows) stringBuilder.append("unable to create table");
        else {
            if (borders) insertCSS("th, td {border: 1px solid black;}");
            insertCSS("th, td {padding: 5px; margin: 5px}");
            stringBuilder.append("<table style=\"border-collapse: collapse;\">");
            for (int i = 0; i < rows; i++) {
                stringBuilder.append("<tr>");
                for (int j = 0; j < columns; j++) {
                    if (i == 0) stringBuilder.append("<th>" + content[j] + "</th>");
                    else stringBuilder.append("<td>" + content[i*columns + j] + "</td>");
                }
                stringBuilder.append("</tr>");
            }
            stringBuilder.append("</table>");
        }
    }

    public static void insertList(String... lines) {
        stringBuilder.append("<ul style=\"list-style-type:disc\">");
        for (int i = 0; i < lines.length; i++)
            stringBuilder.append("<li>" + lines[i] + "</li>");
        stringBuilder.append("</ul>");
    }

    public static void saveFile(String fileName) throws IOException {
        File file = new File("output", fileName);
        // if file does exists, then delete and create a new file
        if (file.exists())
            GlobalAssert.that(file.delete());

        //write to file with OutputStreamWriter
        OutputStream outputStream = new FileOutputStream(file.getAbsoluteFile());
        Writer writer = new OutputStreamWriter(outputStream);
        writer.write(stringBuilder.toString());
        writer.close();

    }
}
