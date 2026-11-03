package Amr.Annotation;

import java.awt.*;
import java.io.FileWriter;

/**
 * Base class for all graphical/editable elements in an AmrDiagram.
 * Defines the common interface for selection, movement, hit-testing, rendering
 * (both AWT painting and SVG export), and XML serialization (save).
 *
 * Most methods here are no-op defaults (empty bodies, "" return, etc.), meant
 * to be overridden by concrete subclasses such as AmrWordObject and
 * AmrConnectionObject. This class itself doesn't appear to be instantiated for
 * real diagram content — it's mainly a shared base/placeholder.
 */
public class AmrObject {

    // Whether this object is currently selected (e.g. for move/copy/delete operations).
    protected boolean selected = false;

    // Whether this object should be rendered in a "highlighted" color (red) vs default (black).
    public boolean colored = false;

    // The rectangular area this object occupies, used for hit-testing, selection-by-area, and centering.
    protected Rectangle boundingBox;

    /** Returns whether this object is currently selected. */
    public boolean isSelected(){
        return selected;
    }

    /**
     * Creates a copy of this object. Note: this base implementation only copies
     * boundingBox — it does NOT copy `selected` or `colored` state. Subclasses
     * presumably override this to also copy their own additional fields (e.g.
     * the underlying AmrWord/AmrConnection reference).
     */
    public AmrObject clone(){
        AmrObject clone = new AmrObject();
        clone.boundingBox = new Rectangle(boundingBox);
        return clone;
    }

    /** No-op by default; subclasses override to actually reposition themselves by (deltaX, deltaY). */
    public void move(int deltaX, int deltaY){}

    /** No-op by default; subclasses override to write their own XML element (e.g. <Word .../>, <Connection .../>) to outfile. */
    public void save(FileWriter outfile){}

    /** Sets this object's selected state. */
    public void select(boolean selected){
        this.selected = selected;
    }

    /** Resizes the bounding box in place, keeping its current x/y position. */
    public void setBoundingBoxSize(int width, int height){
        boundingBox.setSize(width, height);
    }

    /** Hit-test: whether the given point falls within this object's bounding box. */
    public boolean contains(Point p){
        return boundingBox.contains(p);
    }

    /** Returns the center point of this object's bounding box. */
    public Point getCenter(){
        return new Point(boundingBox.x + boundingBox.width / 2, boundingBox.y + boundingBox.height / 2);
    }

    /** Returns an empty string by default; subclasses override to produce their actual SVG markup. */
    public String toSvg(){
        return "";
    }

    /**
     * Sets the drawing color based on `colored` (red if highlighted, black otherwise).
     * Subclasses are expected to call super.paint(g) first to set the color, then
     * perform their own actual drawing using the now-configured Graphics context.
     */
    public void paint(Graphics g){
        if (colored){
            g.setColor(Color.RED);
        } else {
            g.setColor(Color.BLACK);
        }
    }

}