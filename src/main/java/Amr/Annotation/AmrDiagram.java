package Amr.Annotation;

import Amr.Corpus.AmrConnection;
import Amr.Corpus.AmrSentence;
import Amr.Corpus.AmrWord;
import Corpus.FileDescription;

import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Represents the visual/editable diagram for a single AMR-annotated sentence.
 * Wraps an AmrSentence (the underlying data: words + connections) together with
 * a parallel list of AmrObject instances (AmrWordObject / AmrConnectionObject) that
 * know how to draw themselves, hit-test mouse clicks, and serialize to XML/SVG.
 *
 * In effect this is a view/controller layer on top of AmrSentence's model layer.
 */
public class AmrDiagram {

    // Drawable/selectable graphical objects: a mix of AmrWordObject (nodes) and
    // AmrConnectionObject (edges) that mirror the words/connections in `sentence`.
    protected ArrayList<AmrObject> objects;

    // The underlying data model (words + connections) for the sentence being diagrammed.
    private AmrSentence sentence;

    /** Bare constructor used internally (e.g. by clone()) — no sentence attached yet. */
    public AmrDiagram() {
        objects = new ArrayList<>();
    }

    /** Loads the sentence from the given path/file name, then builds the graphical objects from it. */
    public AmrDiagram(String path, String fileName) {
        sentence = new AmrSentence(path, fileName);
        constructObjects();
    }

    /** Loads the sentence from a FileDescription, then builds the graphical objects from it. */
    public AmrDiagram(FileDescription fileDescription) {
        sentence = new AmrSentence(fileDescription);
        constructObjects();
    }

    public String getSentence(){
        return sentence.getSentence();
    }

    public void setSentence(String sentenceText){
        sentence.setSentence(sentenceText);
    }

    /**
     * Computes a bounding rectangle that encloses all word labels in the diagram,
     * based on each word's screen position and an estimated text width
     * (7.3 px per character, a rough heuristic for label width). Each word's
     * footprint is treated as a fixed-height (80px) box around its position,
     * and the result rectangle is grown incrementally to cover every word's box.
     * Connections (edges) are not considered — only AmrWordObject instances.
     */
    public Rectangle getBoundingBox(){
        Rectangle result = new Rectangle();
        boolean first = true;
        for (AmrObject object : objects){
            if (object instanceof AmrWordObject){
                // Rough estimate of rendered text width for this word's label.
                int stringSize = (int) (7.3 * ((AmrWordObject) object).word.getName().length());
                // Top-left corner of this word's bounding box, derived from its center position.
                int x = ((AmrWordObject) object).word.getPosition().x - stringSize - 5;
                int y = ((AmrWordObject) object).word.getPosition().y - 15;
                if (first){
                    // Seed the result rectangle with the first word's box.
                    result = new Rectangle(x, y,  2 * stringSize + 20, 80);
                    first = false;
                } else {
                    // Expand left edge if this word extends further left than current bounds.
                    if (x < result.x){
                        result.width += result.x - x;
                        result.x = x;
                    } else {
                        // Otherwise, expand right edge if this word extends further right.
                        if (x + 2 * stringSize + 20 > result.x + result.width){
                            result.width += x + 2 * stringSize + 20 - result.x - result.width;
                        }
                    }
                    // Expand top edge if this word extends further up than current bounds.
                    if (y < result.y){
                        result.height += result.y - y;
                        result.y = y;
                    } else {
                        // Otherwise, expand bottom edge if this word extends further down.
                        if (y + 80 > result.y + result.height){
                            result.height += y + 80 - result.y - result.height;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Creates a deep-ish copy of this diagram: each AmrObject is cloned individually,
     * but the underlying `sentence` reference is shared (not cloned) between original
     * and copy.
     */
    public AmrDiagram clone() {
        int i;
        AmrDiagram newDiagram = new AmrDiagram();
        for (i = 0; i < objects.size(); i++) {
            newDiagram.objects.add(objects.get(i).clone());
        }
        newDiagram.sentence = sentence;
        return newDiagram;
    }

    /**
     * Rebuilds the `objects` list from scratch based on the current state of `sentence`.
     * For every word in the sentence, creates a corresponding AmrWordObject and indexes
     * it by word name (so connections can look up their endpoints). Then, for every
     * connection in the sentence, creates a corresponding AmrConnectionObject linking
     * the appropriate AmrWordObject instances.
     *
     * Called after loading a sentence, after navigating to a different sentence
     * (previousAmr/nextAmr), or whenever the graphical view needs to be resynced
     * with the underlying data model.
     */
    public void constructObjects(){
        HashMap<String, AmrWordObject> wordObjects = new HashMap<>();
        objects = new ArrayList<>();
        for (int i = 0; i < sentence.wordCount(); i++){
            AmrWord word = (AmrWord) sentence.getWord(i);
            AmrWordObject amrWordObject = new AmrWordObject(word);
            objects.add(amrWordObject);
            wordObjects.put(word.getName(), amrWordObject);
        }
        for (int i = 0; i < sentence.connectionCount(); i++){
            AmrConnection connection = sentence.getConnection(i);
            objects.add(new AmrConnectionObject(wordObjects.get(connection.from().getName()), wordObjects.get(connection.to().getName()), connection.with()));
        }
    }

    /** Returns the raw (unresolved-path) file name of the underlying sentence. */
    public String getFileName(){
        return sentence.getRawFileName();
    }

    /** Returns the folder portion of the underlying sentence's file path. */
    public String getFolder(){
        return sentence.getFolder();
    }

    /**
     * Adds a brand-new word to the diagram at the given position: creates both
     * the graphical AmrWordObject (for display) and the underlying AmrWord
     * (added to the data model via sentence.addWord).
     */
    public void addWord(String name, Point position) {
        objects.add(new AmrWordObject(name, position));
        sentence.addWord(new AmrWord(name, position));
    }

    /**
     * Adds a new connection between two existing word objects: creates both
     * the graphical AmrConnectionObject (for display) and the underlying
     * AmrConnection (added to the data model via sentence.addConnection).
     */
    public void addConnection(AmrWordObject from, AmrWordObject to, String with) {
        objects.add(new AmrConnectionObject(from, to, with));
        sentence.addConnection(from.word, to.word, with);
    }

    /**
     * Serializes the entire diagram to an XML file at the given path: writes an
     * <Amr> root element, then delegates to each AmrObject's own save(outfile)
     * method to write its respective XML element (presumably <Word .../> for
     * AmrWordObject and <Connection .../> for AmrConnectionObject — see note below),
     * then closes with </Amr>. Any IOException is caught and reported via a
     * console message rather than being propagated.
     */
    public void save(String filename) {
        int i;
        FileWriter outfile;
        try {
            outfile = new FileWriter(filename);
            outfile.write("<Amr sentence=\"" + escapeXml(sentence.getSentence()) + "\">\n");
            for (i = 0; i < objects.size(); i++) {
                objects.get(i).save(outfile);
            }
            outfile.write("</Amr>\n");
            outfile.close();
        } catch (IOException ioException) {
            System.out.println("Output file can not be opened");
        }
    }

    /** Convenience overload: saves back to the sentence's own current file name. */
    public void save(){
        save(sentence.getFileName());
    }

    /**
     * Hit-tests a point against all diagram objects to find the closest one.
     * Word objects are checked first and matched exactly (if `p` falls within
     * its bounding box, that word is returned immediately — first match wins,
     * no further comparison). If no word matches, connection objects are checked
     * by computing the perpendicular distance from `p` to the line implied by
     * the connection's bounding box diagonal (treating the box as defining a
     * line from (x1,y1) with slope m = height/width), and the connection with
     * the smallest such distance (among those whose bounding box contains `p`)
     * is returned.
     */
    public AmrObject getNearestAmrObjectAtPos(Point p) {
        int i;
        double minDistance = Integer.MAX_VALUE;
        AmrObject nearestObject = null;
        for (i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof AmrWordObject && objects.get(i).contains(p)) {
                nearestObject = objects.get(i);
                break;
            } else {
                if (objects.get(i) instanceof AmrConnectionObject && objects.get(i).contains(p)){
                    int x1 = objects.get(i).boundingBox.x;
                    int y1 = objects.get(i).boundingBox.y;
                    // Slope of the line formed by the connection's bounding box diagonal.
                    double m = objects.get(i).boundingBox.height / (0.0 + objects.get(i).boundingBox.width);
                    // y-intercept (n) of that line: y = m*x + n  =>  n = y1 - m*x1.
                    double n = y1 - m * x1;
                    // Perpendicular distance from point p to the line m*x - y + n = 0.
                    double distance = Math.abs(m * p.x + n - p.y) / Math.sqrt(m * m + 1);
                    if (distance < minDistance){
                        minDistance = distance;
                        nearestObject = objects.get(i);
                    }
                }
            }
        }
        return nearestObject;
    }

    /** Paints every object in the diagram (words and connections) onto the given Graphics context. */
    public void paint(Graphics graphics) {
        int i;
        for (i = 0; i < objects.size(); i++) {
            objects.get(i).paint(graphics);
        }
    }

    /**
     * Renders the diagram as an SVG document of the given height/width, by
     * wrapping the SVG output of each individual object (via object.toSvg())
     * inside an <svg> root element.
     */
    public String toSvg(int height, int width){
        StringBuilder result = new StringBuilder("<svg height=\"" + height + "\" width=\"" + width + "\">\n");
        for (AmrObject object : objects) {
            result.append(object.toSvg());
        }
        result.append("</svg>\n");
        return result.toString();
    }

    /** Marks every object in the diagram as selected. */
    public void selectAll() {
        int i;
        for (i = 0; i < objects.size(); i++) {
            objects.get(i).select(true);
        }
    }

    /** Returns clones of all currently-selected objects (used for copy/paste). */
    public ArrayList<AmrObject> copyAll() {
        int i;
        ArrayList<AmrObject> result = new ArrayList<>();
        for (i = 0; i < objects.size(); i++) {
            if (objects.get(i).isSelected()) {
                result.add(objects.get(i).clone());
            }
        }
        return result;
    }

    /** Moves every currently-selected object by (deltaX, deltaY). */
    public void moveSelected(int deltaX, int deltaY) {
        int i;
        for (i = 0; i < objects.size(); i++) {
            if (objects.get(i).isSelected()) {
                objects.get(i).move(deltaX, deltaY);
            }
        }
    }

    /**
     * Selects every object whose center point falls within the given rectangle
     * (and deselects every object whose center does not) — i.e. a typical
     * "rubber-band selection" operation.
     */
    public void selectArea(Rectangle area) {
        int i;
        for (i = 0; i < objects.size(); i++) {
            objects.get(i).select(area.contains(objects.get(i).getCenter()));
        }
    }

    /** Appends a previously-copied list of objects into this diagram's object list (paste operation). */
    public void pasteObjects(ArrayList<AmrObject> copyList) {
        int i;
        for (i = 0; i < copyList.size(); i++) {
            objects.add(copyList.get(i));
        }
    }

    /** Deselects every object in the diagram. */
    public void deselectAll() {
        int i;
        for (i = 0; i < objects.size(); i++) {
            objects.get(i).select(false);
        }
    }

    /**
     * Removes every currently-selected object from the diagram.
     * Note: decrementing `i` after a removal correctly compensates for the
     * index shift caused by ArrayList.remove(), so this correctly handles
     * consecutive selected objects without skipping any.
     */
    public void deleteSelected() {
        int i;
        for (i = 0; i < objects.size(); i++) {
            if (objects.get(i).isSelected()) {
                objects.remove(i);
                i--;
            }
        }
    }

    /**
     * Returns the raw file name of the sentence `count` positions earlier in the
     * corpus, without permanently changing the current sentence's position:
     * temporarily shifts the FileDescription's index backward, reads the name,
     * then shifts it back forward to restore the original position.
     */
    public String getPreviousFileName(int count){
        sentence.getFileDescription().addToIndex(-count);
        String fileName = sentence.getRawFileName();
        sentence.getFileDescription().addToIndex(count);
        return fileName;
    }

    /**
     * Returns the raw file name of the sentence `count` positions later in the
     * corpus, without permanently changing the current sentence's position
     * (mirrors getPreviousFileName, but shifts forward then back).
     */
    public String getNextFileName(int count){
        sentence.getFileDescription().addToIndex(count);
        String fileName = sentence.getRawFileName();
        sentence.getFileDescription().addToIndex(-count);
        return fileName;
    }

    /** Navigates the underlying sentence backward by `count` and rebuilds the graphical objects to match. */
    public void previousAmr(int count){
        sentence.previousSentence(count);
        constructObjects();
    }

    /** Navigates the underlying sentence forward by `count` and rebuilds the graphical objects to match. */
    public void nextAmr(int count){
        sentence.nextSentence(count);
        constructObjects();
    }

    /** Checks whether a sentence exists `count` positions before the current one in the corpus. */
    public boolean previousAmrExists(int count){
        return sentence.getFileDescription().previousFileExists(count);
    }

    /** Checks whether a sentence exists `count` positions after the current one in the corpus. */
    public boolean nextAmrExists(int count){
        return sentence.getFileDescription().nextFileExists(count);
    }

    private String escapeXml(String s){
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}