package Amr.Construction;

import AnnotatedSentence.AnnotatedSentence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;

public abstract class AmrConstructionAlgorithm {

    protected abstract ArrayList<String> constructExcelAmr(AnnotatedSentence sentence);

    public void saveAmr(AnnotatedSentence sentence, String fileName){
        ArrayList<String> items = constructExcelAmr(sentence);
        items.remove(0);
        if (items.size() > 0){
            saveAmr(items, fileName, sentence.toWords());
        }
    }

    public String toString(AnnotatedSentence sentence){
        StringBuilder result = new StringBuilder();
        for (String item : constructExcelAmr(sentence)){
            result.append(item).append("\n");
        }
        return result.toString();
    }

    private String escapeXml(String s){
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public void saveAmr(ArrayList<String> items, String fileName){
        saveAmr(items, fileName, "");
    }

    public void saveAmr(ArrayList<String> items, String fileName, String sentenceText){
        String xml = buildAmrXml(items, sentenceText);
        try {
            PrintWriter output = new PrintWriter(fileName);
            output.print(xml);
            output.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds the full <Amr>...</Amr> XML representation of `items` as a String,
     * without writing it anywhere. This is the same logic previously inlined
     * directly into saveAmr(), extracted so callers (like a batch generator)
     * can collect XML for many sentences and write them out together.
     */
    public String buildAmrXml(ArrayList<String> items, String sentenceText){
        StringBuilder output = new StringBuilder();
        int startX = 750, startY = 100;
        String[] lastParent = new String[50];
        int[] lastX = new int[50];
        int[] childCount = new int[50];
        HashSet<String> words = new HashSet<>();

        output.append("<Amr sentence=\"").append(escapeXml(sentenceText)).append("\">\n");
        String line = items.get(0);
        output.append("<Word name=\"").append(line).append("\" positionX=\"").append(startX).append("\" positionY=\"").append(startY).append("\"/>\n");
        words.add(line);
        lastParent[0] = line;
        childCount[0] = 0;
        lastX[0] = startX;
        for (int j = 1; j < items.size(); j++){
            line = items.get(j);
            int tabCount = 0, i = 0;
            while (line.charAt(i) == '\t'){
                tabCount++;
                i++;
            }
            line = line.substring(tabCount);
            if (line.contains(":")){
                lastParent[tabCount] = line.substring(0, line.lastIndexOf(":"));
            } else {
                lastParent[tabCount] = line;
            }
            childCount[tabCount] = 0;
            lastX[tabCount] = lastX[tabCount - 1] + (childCount[tabCount - 1] - 1) * 100;
            if (line.contains(":")){
                if (!words.contains(line.substring(0, line.lastIndexOf(":")))){
                    output.append("<Word name=\"").append(line.substring(0, line.lastIndexOf(":"))).append("\" positionX=\"").append(lastX[tabCount]).append("\" positionY=\"").append(startY + 100 * tabCount).append("\"/>\n");
                    words.add(line.substring(0, line.lastIndexOf(":")));
                }
                output.append("<Connection from=\"").append(lastParent[tabCount - 1]).append("\" to=\"").append(line.substring(0, line.lastIndexOf(":"))).append("\" with=\"").append(line.substring(line.lastIndexOf(":") + 1)).append("\"/>\n");
            } else {
                if (!words.contains(line)){
                    output.append("<Word name=\"").append(line).append("\" positionX=\"").append(lastX[tabCount]).append("\" positionY=\"").append(startY + 100 * tabCount).append("\"/>\n");
                    words.add(line);
                }
                output.append("<Connection from=\"").append(lastParent[tabCount - 1]).append("\" to=\"").append(line).append("\"/>\n");
            }
            childCount[tabCount - 1]++;
        }
        output.append("</Amr>\n");
        return output.toString();
    }

    private String getAmrOutputFileName(String line){
        String folder = ".";
        if (line.contains("/")){
            folder = line.substring(0, line.indexOf("/"));
            File file = new File("./" + folder);
            if (!file.exists()){
                file.mkdir();
            }
            line = line.substring(line.indexOf("/") + 1);
        }
        return folder + "/" + line;
    }

    public void readAndSaveMultipleAmr(String fileName) throws FileNotFoundException {
        ArrayList<String> lines = new ArrayList<>();
        String outputFileName = null;
        boolean firstLine = false, skipFile = false;
        Scanner input = new Scanner(new File(fileName));
        while (input.hasNext()){
            String line = input.nextLine();
            if (line.matches(".*[0-9]+\\.(train|test|dev)")){
                firstLine = true;
                skipFile = false;
                outputFileName = getAmrOutputFileName(line);
                lines = new ArrayList<>();
            } else {
                if (line.isEmpty()){
                    if (!skipFile){
                        saveAmr(lines, outputFileName);
                    }
                } else {
                    if (firstLine){
                        if (line.startsWith("\t")){
                            skipFile = true;
                        } else {
                            lines.add(line);
                        }
                        firstLine = false;
                    } else {
                        if (!skipFile){
                            lines.add(line);
                        }
                    }
                }
            }
        }
        saveAmr(lines, outputFileName);
    }

}