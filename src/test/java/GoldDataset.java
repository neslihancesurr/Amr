import Amr.Corpus.AmrSentence;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Reads the XML gold files directly from Dropbox (including frame subfolders)
// -> uses toString/wordToString to write Penman
// -> writes output to src/outputs/gold/framenet_penman_gold.txt
public class GoldDataset {

    public static void main(String[] args) {

        String DATASET = "Gb";
        String dropboxFolderPath = "/Users/neslihancesur/Library/CloudStorage/Dropbox/" + DATASET + "/Amr";
        String outputFilePath = "src/outputs/gold/" + DATASET + "_penman_gold.txt";

        File outputFile = new File(outputFilePath);
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(Paths.get(dropboxFolderPath))) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".dev") || n.endsWith(".test") || n.endsWith(".train");
                    })
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (files.isEmpty()) {
            System.out.println("No files found.");
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            for (Path file : files) {
                String name = file.getFileName().toString();
                String folderPath = file.getParent().toString();
                String folderName = file.getParent().getFileName().toString();
                String label = folderName + "/" + name;

                try {
                    // Same call as before, but with the file's own folder as the path
                    AmrSentence sentence = new AmrSentence(folderPath, name);

                    // First line is "# ::id <rawFileName>" -> prefix it with the frame folder
                    String penman = sentence.toString().replaceFirst(
                            "^# ::id ",
                            Matcher.quoteReplacement("# ::id " + folderName + "/"));

                    writer.write(penman);
                    writer.newLine();
                    writer.newLine();

                    System.out.println("Processed: " + label);

                } catch (StackOverflowError e) {
                    System.err.println("SKIPPED: " + label + " (StackOverflowError - problematic graph)");
                } catch (Exception e) {
                    System.err.println("Error processing " + label + ": " + e.getMessage());
                }
            }

            System.out.println("Finished. Output written to:");
            System.out.println(outputFilePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}