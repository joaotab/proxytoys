package minimesh;

import com.opensymphony.module.sitemesh.html.BasicRule;
import com.opensymphony.module.sitemesh.html.CustomTag;
import com.opensymphony.module.sitemesh.html.HTMLProcessor;
import com.opensymphony.module.sitemesh.html.Tag;
import com.opensymphony.module.sitemesh.html.rules.BodyTagRule;
import com.opensymphony.module.sitemesh.html.rules.HeadExtractingRule;
import com.opensymphony.module.sitemesh.html.rules.MetaTagRule;
import com.opensymphony.module.sitemesh.html.rules.PageBuilder;
import com.opensymphony.module.sitemesh.html.rules.TitleExtractingRule;
import com.opensymphony.module.sitemesh.html.util.CharArray;

import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;


/**
 * A single page in a website, including title, filename and content. All this information is loaded from an HTML file (using
 * the SiteMesh library).
 * 
 * @author Joe Walnes
 */
public class Page {

    private Properties properties;
    private String filename;
    private String head;
    private String body;
    private Collection links = new HashSet();

    public Page(File htmlFile) {
        try {
            filename = htmlFile.getName();
            FileSystem fileSystem = new FileSystem();
            char[] rawHTML = fileSystem.readFile(htmlFile);
            extractContentFromHTML(rawHTML);
        } catch (IOException e) {
            throw new CannotParsePageException(e);
        }
    }

    public Page(String filename, String htmlContent) {
        try {
            this.filename = filename;
            extractContentFromHTML(htmlContent.toCharArray());
        } catch (IOException e) {
            throw new CannotParsePageException(e);
        }
    }

    private void extractContentFromHTML(char[] rawHTML) throws IOException {
        // where to dump properties extracted from the page
        properties = new Properties();
        PageBuilder pageBuilder = new PageBuilder() {
            public void addProperty(String key, String value) {
                properties.setProperty(key, value);
            }
        };

        // buffers to hold head and body content
        CharArray headBuffer = new CharArray(64);
        CharArray bodyBuffer = new CharArray(4096);

        // setup rules for html processor
        HTMLProcessor htmlProcessor = new HTMLProcessor(rawHTML, bodyBuffer);
        htmlProcessor.addRule(new BodyTagRule(pageBuilder, bodyBuffer));
        htmlProcessor.addRule(new HeadExtractingRule(headBuffer));
        htmlProcessor.addRule(new TitleExtractingRule(pageBuilder));
        htmlProcessor.addRule(new MetaTagRule(pageBuilder));
        htmlProcessor.addRule(new LinkExtractingRule());
        htmlProcessor.addRule(new AddFirstChildClassToHeader());
        // turn JIRA:XSTR-123 snippets into links
        htmlProcessor.addTextFilter(new OROReplacementTextFilter(
                "JIRA:(PTOYS\\-[0-9]+)", "<a href=\"http://jira.codehaus.org/browse/$1\">$1</a>"));

        // go!
        htmlProcessor.process();
        this.head = headBuffer.toString();
        this.body = bodyBuffer.toString();
    }

    public String getTitle() {
        if (properties.containsKey("meta.short")) {
            return properties.getProperty("meta.short");
        } else {
            return properties.getProperty("title");
        }
    }

    public String getHead() {
        return head.toString();
    }

    public String getBody() {
        return body.toString();
    }

    public String getFilename() {
        return filename;
    }

    public String getHref() {
        return getFilename();
    }

    public Collection getLinks() {
        return Collections.unmodifiableCollection(links);
    }

    public static class CannotParsePageException extends RuntimeException {
        public CannotParsePageException(Throwable cause) {
            super(cause.getMessage());
        }
    }

    /**
     * Rule for HTMLProcessor that records all <a href=""> links.
     */
    private class LinkExtractingRule extends BasicRule {
        public boolean shouldProcess(String tag) {
            return tag.equalsIgnoreCase("a");
        }

        public void process(Tag tag) {
            if (tag.hasAttribute("href", false)) {
                links.add(tag.getAttributeValue("href", false));
            }
            tag.writeTo(currentBuffer());
        }
    }

    /**
     * Rule for HTMLProcessor that adds class=""FirstChild" to the first header of the body if it is the first element.
     */
    private class AddFirstChildClassToHeader extends BasicRule {
        private boolean firstChildIsHeader = true;
        private final PatternMatcher matcher;
        private final Pattern pattern;
        
        private AddFirstChildClassToHeader() {
            matcher = new Perl5Matcher();
            try {
                pattern = new Perl5Compiler().compile("^[hH][1-9]$");
            } catch (MalformedPatternException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        public boolean shouldProcess(String tag) {
            return tag.equalsIgnoreCase("p") || matcher.contains(tag,pattern);
        }

        public void process(Tag tag) {
            if (firstChildIsHeader) {
                if (!tag.getName().equalsIgnoreCase("p")) {
                    CustomTag customTag = new CustomTag(tag);
                    customTag.addAttribute("class", "FirstChild");
                    tag = customTag;
                }
                firstChildIsHeader = false;
            }
            tag.writeTo(currentBuffer());
        }
    }

}