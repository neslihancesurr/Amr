import Amr.Construction.RuleBasedConstructionAlgorithm;
import Amr.Corpus.AmrSentence;
import AnnotatedSentence.AnnotatedSentence;
import WordNet.WordNet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// reads TURKISH PHRASE files from Dropbox (NOT XML) -> gives them to rule based algorithm (constructexcelamr and saveAMR) ->
// writes XML output FOLDER to src/outputs/parser/{DATASETNAME}_parser_xml
// -> writes TXT output FILE to src/outputs/parser/{DATASETNAME}_parser_excel.txt
// Works for flat datasets (Gb, ...) and for datasets with subfolders (Framenet-Examples):
// subfolders are mirrored in the XML output folder, and the subfolder name is added to the Penman "# ::id" line.

public class AMRGenerator {

    public static void main(String[] args) {

        String DATASET = "Framenet-Examples";
        // 1. Define your paths
        String dropboxFolderPath = "/Users/neslihancesur/Library/CloudStorage/Dropbox/" + DATASET + "/Turkish-Phrase";
        // Define the output text file
        String outputFilePath = "/Users/neslihancesur/IdeaProjects/Amr2/src/outputs/parser/" + DATASET + "_parser_excel.txt";
        // Define the folder where one XML file per sentence will be written
        String xmlOutputFolderPath = "/Users/neslihancesur/IdeaProjects/Amr2/src/outputs/parser/" + DATASET + "_parser_xml";

        File corpusFolder = new File(dropboxFolderPath);
        File outputFile = new File(outputFilePath);
        File xmlOutputFolder = new File(xmlOutputFolderPath);

        // Create the parent directory for the output text file if it doesn't exist
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        // Create the XML output folder if it doesn't exist
        if (!xmlOutputFolder.exists()) {
            xmlOutputFolder.mkdirs();
        }

        // 2. Initialize WordNet (update with your actual path)
        WordNet wordNet = new WordNet();

        // 3. Initialize the Construction Algorithm
        RuleBasedConstructionAlgorithm amrAlgorithm = new RuleBasedConstructionAlgorithm(wordNet);

        // 4. Open the single text-file writer; XML files are written individually per sentence
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            System.out.println("--- Processing Training Files ---");
            processCorpus(corpusFolder, ".train", amrAlgorithm, writer, xmlOutputFolder);

            System.out.println("--- Processing Testing Files ---");
            processCorpus(corpusFolder, ".test", amrAlgorithm, writer, xmlOutputFolder);

            System.out.println("--- Processing Testing Files ---");
            processCorpus(corpusFolder, ".dev", amrAlgorithm, writer, xmlOutputFolder);

            System.out.println("All AMR outputs successfully saved to: " + outputFilePath);
            System.out.println("All AMR XML files saved to folder: " + xmlOutputFolderPath);

        } catch (IOException e) {
            System.err.println("Failed to write to the output file: " + e.getMessage());
        }

        // Get the XML outputs and produces Penman using the toString/wordToString and saves as txt

        String penmanOutputPath = "/Users/neslihancesur/IdeaProjects/Amr2/src/outputs/parser/" + DATASET + "_parser_penman.txt";
        XMLToPenman(xmlOutputFolderPath, penmanOutputPath);
    }

    /**
     * Relative path of the file's folder with respect to the corpus root, using "/" as separator.
     * Returns "" when the file sits directly in the root (flat datasets), "Abandoned_State" for Framenet.
     */
    private static String relativeFolder(File root, File file) {
        Path rel = root.toPath().relativize(file.getParentFile().toPath());
        return rel.toString().replace(File.separatorChar, '/');
    }

    /** Recursively collects regular files whose name satisfies the filter, sorted by path. */
    private static List<File> collectFiles(File root, java.util.function.Predicate<String> nameFilter) throws IOException {
        try (Stream<Path> walk = Files.walk(root.toPath())) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> nameFilter.test(p.getFileName().toString()))
                    .sorted()
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
    }

    private static void processCorpus(File folder, String pattern, RuleBasedConstructionAlgorithm algorithm, BufferedWriter writer, File xmlOutputFolder) {
        // Find files matching the pattern (also inside subfolders), sorted by path
        List<File> files;
        try {
            files = collectFiles(folder, name -> name.endsWith(pattern));
        } catch (IOException e) {
            System.err.println("Could not read folder " + folder + ": " + e.getMessage());
            return;
        }

        if (files.isEmpty()) {
            System.out.println("No files found matching pattern: " + pattern);
            return;
        }

        // Loop through the raw files
        for (File file : files) {
            String relFolder = relativeFolder(folder, file);
            String label = relFolder.isEmpty() ? file.getName() : relFolder + "/" + file.getName();

            try {
                // Create the AnnotatedSentence directly from the text file
                AnnotatedSentence annotatedSentence = new AnnotatedSentence(file);

                // Construct the AMR structure
                ArrayList<String> amrOutput = algorithm.constructExcelAmr(annotatedSentence);

                // Write the output array to our text file
                for (String line : amrOutput) {
                    writer.write(line);
                    writer.newLine(); // Move to the next line
                }

                // Add an empty line to separate sentences visually
                writer.newLine();

                // Strip the header/identifier line (same convention as saveAmr(AnnotatedSentence, String))
                // and write this sentence's own XML file, named after the source file.
                ArrayList<String> amrItems = new ArrayList<>(amrOutput);
                amrItems.remove(0);

                if (!amrItems.isEmpty()) {
                    String baseName = file.getName().replaceFirst("\\.[^.]+$", "");

                    // Mirror the subfolder (empty for flat datasets -> same location as before)
                    File targetDir = relFolder.isEmpty() ? xmlOutputFolder : new File(xmlOutputFolder, relFolder);
                    if (!targetDir.exists()) {
                        targetDir.mkdirs();
                    }
                    String xmlFileName = new File(targetDir, baseName + pattern).getPath();

                    algorithm.saveAmr(amrItems, xmlFileName, annotatedSentence.toWords());
                }

                System.out.println("Processed and appended: " + label);

            } catch (Exception e) {
                System.err.println("Error processing file " + label + ": " + e.getMessage());
            }
        }
    }

    // this will read separate XML files which are outputs of the parser and turn each into penman and write them into the same file
    private static void XMLToPenman(String inputFolderPath, String outputFilePath){

        File inputFolder = new File(inputFolderPath);
        File outputFile = new File(outputFilePath);

        if (outputFile.getParentFile() != null &&
                !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }

        List<File> files;
        try {
            // Only corpus files (digits + extension), also inside subfolders
            files = collectFiles(inputFolder, name -> name.matches("\\d+\\.(test|train|dev)"));
        } catch (IOException e) {
            System.out.println("No files found.");
            return;
        }

        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(outputFile))) {

            for (File file : files) {

                String name = file.getName();
                String relFolder = relativeFolder(inputFolder, file);
                String label = relFolder.isEmpty() ? name : relFolder + "/" + name;

                try {
                    // Each file is loaded from its own folder, so equal IDs in different frames don't clash
                    AmrSentence sentence =
                            new AmrSentence(file.getParent(), name);

                    String penman = sentence.toString();

                    // Subfolder (e.g. Framenet frame) -> prefix the id line; flat datasets stay unchanged
                    if (!relFolder.isEmpty()) {
                        penman = penman.replaceFirst(
                                "^# ::id ",
                                Matcher.quoteReplacement("# ::id " + relFolder + "/"));
                    }

                    writer.write(penman);
                    writer.newLine();
                    writer.newLine();

                    System.out.println("Processed: " + label);

                } catch (StackOverflowError e) {
                    // Skip problematic AMR graphs
                    System.err.println(
                            "SKIPPED: " + label +
                                    " (StackOverflowError - problematic graph)"
                    );

                } catch (Exception e) {
                    System.err.println(
                            "Error processing " + label +
                                    ": " + e.getMessage()
                    );
                }
            }

            System.out.println("Finished. Output written to:");
            System.out.println(outputFilePath);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}