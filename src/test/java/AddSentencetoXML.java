import AnnotatedSentence.AnnotatedSentence;
import Amr.Annotation.AmrDiagram.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// old xml, annotated sentence files -> new xml with the sentence attribute
// Works for flat datasets (GB) and for datasets with subfolders (Framenet-Examples):
// the sentence file is looked up at the same relative path, and the output mirrors the subfolders.
public class AddSentencetoXML {

    public static void main(String[] args) throws IOException {
        // String baseFolder = "/Users/neslihancesur/Desktop/yedek dropbox/GB";
        String baseFolder = "/Users/neslihancesur/Desktop/yedek dropbox/Framenet-Examples";

        String folderPath = baseFolder + "/Amr";
        String outputFolderPath = baseFolder + "/Amr_new";
        String sentencePath = baseFolder + "/Turkish-Phrase";

        File oldFolder = new File(folderPath);
        File newFolder = new File(outputFolderPath);
        File sentenceFolder = new File(sentencePath);

        if (!oldFolder.isDirectory() || !sentenceFolder.isDirectory()) {
            System.out.println("Could not read input folders.");
            return;
        }

        if (!newFolder.exists()) {
            newFolder.mkdirs();
        }

        // All corpus files, also inside subfolders, sorted by path
        List<File> files;
        try (Stream<Path> walk = Files.walk(oldFolder.toPath())) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".dev") || n.endsWith(".test") || n.endsWith(".train");
                    })
                    .sorted()
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }

        for (File file : files) {

            // Relative path of this file w.r.t. the Amr folder, e.g. "Abandoned_State/0000.train"
            // (just "0000.train" for flat datasets)
            Path relPath = oldFolder.toPath().relativize(file.toPath());
            String label = relPath.toString().replace(File.separatorChar, '/');

            // Find the sentence file at the same relative path
            File sentenceFile = sentenceFolder.toPath().resolve(relPath).toFile();

            if (!sentenceFile.isFile()) {
                System.out.println("No matching sentence file for: " + label);
                continue;
            }

            // Load the annotation file
            AnnotatedSentence annotatedSentence = new AnnotatedSentence(sentenceFile);

            // Get the actual Turkish sentence
            String sentence = annotatedSentence.toWords();

            // Read the original XML
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

            if (lines.isEmpty()) {
                System.out.println("Empty XML file: " + label);
                continue;
            }

            // Replace <Amr> with <Amr sentence="...">
            if (lines.get(0).trim().equals("<Amr>")) {
                lines.set(0, "<Amr sentence=\"" + escapeXML(sentence) + "\">");
            } else {
                System.out.println("Unexpected first line in: " + label);
                continue;
            }

            // Create output file at the same relative path (creating the subfolder if needed)
            File outputFile = newFolder.toPath().resolve(relPath).toFile();
            outputFile.getParentFile().mkdirs();

            // Write modified XML
            Files.write(outputFile.toPath(), lines, StandardCharsets.UTF_8);

            System.out.println("Processed: " + label);
        }
    }

    private static String escapeXML(String sentence) {
        return sentence.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}