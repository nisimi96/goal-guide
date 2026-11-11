package com.goalguide.guide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * The guide is a plugin anyone can use, with no automation. Its code may name only its own packages (com.goalguide),
 * RuneLite's own plugins it reads (Time Tracking, Bank Tags) and the plugin framework. Its code and data, comments
 * and text included, must not name a bot or any other project.
 */
public class GuideIsBotFreeTest
{
    static final List<String> ROOTS = List.of("src/main/java", "src/main/resources");

    /** What guide code may name under net.runelite.client.plugins. */
    static final Set<String> ALLOWED = Set.of(
        // RuneLite's own Time Tracking plugin: the farming timers
        "timetracking",
        // RuneLite's own Bank Tags plugin: the bank button shows the step's items as a tag
        "banktags",
        // the plugin framework itself
        "Plugin", "PluginDescriptor", "PluginDependency", "PluginManager", "PluginInstantiationException", "PluginType", "PluginPanel");

    private static final Pattern REF = Pattern.compile("net\\.runelite\\.client\\.plugins\\.([A-Za-z0-9_]+)");

    /** Only com.goalguide may hold code. */
    @Test
    public void allCodeIsInTheGuidePackage() throws IOException
    {
        List<String> bad = new ArrayList<>();
        for (Path f : files("src/main/java"))
            if (f.toString().endsWith(".java") && !f.toString().replace('\\', '/').startsWith("src/main/java/com/goalguide/"))
                bad.add(f.toString());
        assertTrue("code outside com.goalguide: " + bad, bad.isEmpty());
    }

    @Test
    public void theGuideNamesNoOtherPlugins() throws IOException
    {
        List<String> bad = new ArrayList<>();
        for (Path f : files("src/main/java"))
        {
            if (!f.toString().endsWith(".java")) continue;
            Matcher m = REF.matcher(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
            while (m.find())
                if (!ALLOWED.contains(m.group(1))) bad.add(f.getFileName() + " names " + m.group());
        }
        assertTrue("guide code may only use RuneLite's own plugins: " + bad, bad.isEmpty());
    }

    /** Words that name bots or other projects. Config key "ironloop" is a key, not a word. */
    static final Pattern WORDS = Pattern.compile("\\b((?i:bots?|Traveler|SmartWalker|runners?|aio|aio-runs|run log|RunLog|dry run|your turn|auto loop"
        + "|Iron Loop|Microbot|yuval|humaninput|loadout|BotSession)|AIOs?|Aio[A-Z]\\w*|Auto [A-Z][a-z]+)\\b");

    @Test
    public void theGuideNamesNoBotOrProject() throws IOException
    {
        List<String> bad = new ArrayList<>();
        for (String root : ROOTS)
            for (Path f : files(root))
            {
                String name = f.getFileName().toString();
                if (!name.endsWith(".java") && !name.endsWith(".tsv") && !name.endsWith(".txt") && !name.endsWith(".json")) continue;
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++)
                {
                    Matcher m = WORDS.matcher(lines.get(i));
                    if (m.find()) bad.add(name + ":" + (i + 1) + " says \"" + m.group() + "\"");
                }
            }
        assertTrue("the guide must not name a bot or another project: " + bad, bad.isEmpty());
    }

    private static List<Path> files(String root) throws IOException
    {
        Path dir = Paths.get(root);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir))
        {
            List<Path> out = new ArrayList<>();
            s.filter(Files::isRegularFile).forEach(out::add);
            return out;
        }
    }
}
