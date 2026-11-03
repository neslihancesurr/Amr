package Amr.Annotation;

import Amr.Corpus.AmrWord;
import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Graphical representation of a single AMR word/node. Wraps an AmrWord
 * (the underlying data: name + position) and adds drawing, hit-testing,
 * SVG export, and XML serialization on top of it.
 *
 * Visually, a word is rendered as an oval containing its text label.
 */
public class AmrWordObject extends AmrObject {

    // The underlying data model for this word (name + position).
    protected AmrWord word;

    // Fixed height (in pixels) used for the oval drawn around the word's label.
    static final int OVAL_HEIGHT = 40;

    /** Creates a new AmrWordObject (and its backing AmrWord) from scratch, at the given position. */
    public AmrWordObject(String name, Point position){
        this.word = new AmrWord(name, position);
        this.boundingBox = new Rectangle(position);
    }

    /** Wraps an existing AmrWord (e.g. one loaded from XML) in a graphical object. */
    public AmrWordObject(AmrWord word){
        this.word = word;
        this.boundingBox = new Rectangle(word.getPosition());
    }

    /** Returns the word's name (its label / node identifier). */
    public String getName(){
        return word.getName();
    }

    /** Updates the word's name (label / node identifier). */
    public void setName(String name){
        word.setName(name);
    }

    /** Creates an independent copy of this word object, backed by a brand-new AmrWord with the same name/position. */
    public AmrWordObject clone(){
        return new AmrWordObject(word.getName(), word.getPosition());
    }

    /**
     * Writes this word out as a single self-closing XML element:
     *   <Word name="..." positionX="..." positionY="..."/>
     * matching exactly what AmrSentence.loadFromXml() expects to read for a
     * "Word" element (name, positionX, positionY attributes).
     */
    public void save(FileWriter outfile){
        try{
            outfile.write("<Word name=\""+ this.getName() + "\" positionX=\"" + this.word.getPosition().x + "\" positionY=\"" + this.word.getPosition().y + "\"/>\n");
        }
        catch (IOException ioException){
            System.out.println("Output file can not be opened");
        }
    }

    /**
     * Paints this word: first lets the base class set the stroke color
     * (red if highlighted, black otherwise), then — if selected — draws four
     * small handle circles at the corners of the bounding box. Then measures
     * the label's rendered width using the current font, draws an oval sized
     * to fit the label, draws the label text inside it, and finally updates
     * this object's bounding box to match the oval's actual size (so future
     * hit-testing/selection reflects the rendered size).
     */
    public void paint(Graphics g){
        super.paint(g);
        if (isSelected()){
            g.drawOval(boundingBox.x - 2, boundingBox.y - 2, 4, 4);
            g.drawOval(boundingBox.x - 2, boundingBox.y + boundingBox.height - 2, 4, 4);
            g.drawOval(boundingBox.x + boundingBox.width - 2, boundingBox.y - 2, 4, 4);
            g.drawOval(boundingBox.x + boundingBox.width - 2, boundingBox.y + boundingBox.height - 2, 4, 4);
        }
        int stringSize = g.getFontMetrics().stringWidth(word.getName());
        g.drawOval(word.getPosition().x, word.getPosition().y, stringSize + 10, OVAL_HEIGHT);
        g.drawString(word.getName(), word.getPosition().x + 5, word.getPosition().y + OVAL_HEIGHT / 2 + 5);
        setBoundingBoxSize(stringSize + 10, OVAL_HEIGHT);
    }

    /**
     * Renders this word as SVG: an <ellipse> sized/positioned to roughly match
     * the oval drawn in paint() (using the same 7.3-px-per-character width
     * heuristic seen in AmrDiagram.getBoundingBox(), rather than actual font
     * metrics), plus a <text> element with the word's label.
     */
    public String toSvg(){
        String result = "";
        int stringSize = (int) (7.3 * word.getName().length());
        result += "<ellipse rx=\"" + (stringSize / 2 + 10) + "\" ry=\"" + (OVAL_HEIGHT / 2) + "\" cx=\"" + (word.getPosition().x + 5) + "\" cy=\"" + (word.getPosition().y + OVAL_HEIGHT / 2 + 5) + "\"/>\n";
        result += "<text x=\"" + (word.getPosition().x - stringSize / 2 + 5) + "\" y=\"" + (word.getPosition().y + OVAL_HEIGHT / 2 + 10) + "\">" + word.getName() + "</text>\n";
        return result;
    }

    /** Moves the underlying word's position by (deltaX, deltaY), then syncs the bounding box's location to match. */
    public void move(int deltaX, int deltaY){
        word.move(deltaX, deltaY);
        this.boundingBox.setLocation(word.getPosition());
    }

    /** Returns the word's name (for debugging/display purposes). */
    public String toString(){
        return word.getName();
    }

}