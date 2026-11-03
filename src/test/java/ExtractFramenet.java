

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public class ExtractFramenet {
    public static void main(String[] args) {
        String dropboxFolderPath = "/Users/neslihancesur/Library/CloudStorage/Dropbox/Framenet-Examples/Amr";
        String outputFolderPath = "/Users/neslihancesur/Desktop/Framenet_Extracted_Gold";

        Path sourceDir = Paths.get(dropboxFolderPath);
        Path targetDir = Paths.get(outputFolderPath);

        try {
            // Create the output directory if it doesn't exist
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // Using arrays to keep track of how many files we processed
            int[] devCount = {0};
            int[] testCount = {0};
            int[] trainCount = {0};

            // Recursively walk through all folders and subfolders
            try (Stream<Path> paths = Files.walk(sourceDir)) {
                paths.filter(Files::isRegularFile).forEach(file -> {
                    String originalName = file.getFileName().toString();
                    String lowerCaseName = originalName.toLowerCase();

                    // Check if it's one of our target extensions
                    if (lowerCaseName.endsWith(".dev") || lowerCaseName.endsWith(".test") || lowerCaseName.endsWith(".train")) {
                        try {
                            // Get the immediate parent folder name
                            String folderName = file.getParent().getFileName().toString();

                            // Create the new name: folderName-originalName (e.g., FolderA-0000.dev)
                            String newName = folderName + "-" + originalName;

                            // Copy the file
                            Files.copy(file, targetDir.resolve(newName), StandardCopyOption.REPLACE_EXISTING);

                            // Update counters for the summary
                            if (lowerCaseName.endsWith(".dev")) devCount[0]++;
                            else if (lowerCaseName.endsWith(".test")) testCount[0]++;
                            else trainCount[0]++;

                        } catch (IOException e) {
                            System.err.println("Failed to copy file: " + file + " - " + e.getMessage());
                        }
                    }
                });
            }

            System.out.println("Extraction and renaming completed successfully!");
            System.out.println("Total Dev files copied: " + devCount[0]);
            System.out.println("Total Test files copied: " + testCount[0]);
            System.out.println("Total Train files copied: " + trainCount[0]);

        } catch (IOException e) {
            System.err.println("Error accessing directories: " + e.getMessage());
        }
    }
}