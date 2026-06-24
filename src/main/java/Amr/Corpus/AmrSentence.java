package Amr.Corpus;

import Corpus.FileDescription;
import Corpus.Sentence;
import Dictionary.Word;
import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class AmrSentence extends Sentence {
    private ArrayList<AmrConnection> connections;
    private final FileDescription fileDescription;

    public AmrSentence(String path, String fileName){
        this(new FileDescription(path, fileName));
    }

    public AmrSentence(FileDescription fileDescription){
        this.fileDescription = fileDescription;
        connections = new ArrayList<>();
        reload();
    }

    public void reload(){
        DOMParser parser = new DOMParser();
        Document doc;
        try {
            parser.parse(fileDescription.getFileName());
        } catch (SAXException | IOException ignored) {
        }
        doc = parser.getDocument();
        if (doc != null) {
            loadFromXml(doc.getFirstChild());
        }
    }

    public String getRawFileName(){
        return fileDescription.getRawFileName();
    }

    public String getFileName(){
        return fileDescription.getFileName();
    }

    public String getFolder(){
        return fileDescription.getPath().substring(fileDescription.getPath().lastIndexOf("/"));
    }

    public FileDescription getFileDescription(){
        return fileDescription;
    }

    private AmrWord getWord(String name) {
        for (Word word : words) {
            if (word.getName().equals(name)){
                return (AmrWord) word;
            }
        }
        return null;
    }

    public AmrConnection getConnection(int index){
        return connections.get(index);
    }

    public int connectionCount(){
        return connections.size();
    }

    public void addConnection(AmrWord from, AmrWord to, String with){
        AmrConnection connection = new AmrConnection(from, to, with);
        connections.add(connection);
    }

    private void loadFromXml(Node rootNode) {
        Node objectNode;
        NamedNodeMap attributes;
        String objectName;
        AmrWord from, to;
        String with;
        words.clear();
        objectNode = rootNode.getFirstChild();
        while (objectNode != null) {
            if (objectNode.hasAttributes()) {
                objectName = objectNode.getNodeName();
                attributes = objectNode.getAttributes();
                if (objectName.equalsIgnoreCase("Word")) {
                    AmrWord word = new AmrWord(attributes.getNamedItem("name").getNodeValue(), new Point(Integer.parseInt(attributes.getNamedItem("positionX").getNodeValue()), Integer.parseInt(attributes.getNamedItem("positionY").getNodeValue())));
                    addWord(word);
                } else {
                    if (objectName.equalsIgnoreCase("Connection")) {
                        from = getWord(attributes.getNamedItem("from").getNodeValue());
                        to = getWord(attributes.getNamedItem("to").getNodeValue());
                        if (attributes.getNamedItem("with") != null) {
                            with = attributes.getNamedItem("with").getNodeValue();
                        } else {
                            with = "";
                        }
                        if (from != null && to != null){
                            addConnection(from, to, with);
                        }
                    }
                }
            }
            objectNode = objectNode.getNextSibling();
        }
    }

    public void previousSentence(int count){
        if (fileDescription.previousFileExists(count)){
            fileDescription.addToIndex(-count);
            connections = new ArrayList<>();
            reload();
        }
    }

    public void nextSentence(int count){
        if (fileDescription.nextFileExists(count)){
            fileDescription.addToIndex(count);
            connections = new ArrayList<>();
            reload();
        }
    }

    private AmrWord getRoot(){
        HashSet<AmrWord> done = new HashSet<>();
        for (int i = 0; i < words.size(); i++){
            done.add((AmrWord) getWord(i));
        }
        for (AmrConnection connection : connections){
            done.remove(connection.to());
        }
        return done.iterator().next();
    }

    private String wordToString(AmrWord word, int tabCount){
        String result = "(" + word.getName();
        for (AmrConnection connection : connections){
            if (connection.from().equals(word)){
                String child = "\n";
                for (int i = 1; i <= tabCount + 1; i++){
                    child += "\t";
                }
                child += ":" + connection.with();
                child += " " + wordToString(connection.to(), tabCount + 1);
                result += child;
            }
        }
        return result + ")";
    }

    public String toString(){
        return getFileName() + "\n" + wordToString(getRoot(), 0);
    }

}
