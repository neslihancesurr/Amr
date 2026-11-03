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
import java.util.*;
import java.util.List;

/**
 * Represents a single AMR (Abstract Meaning Representation) annotated sentence.
 * Each sentence is backed by an XML file on disk (described by {@link FileDescription}),
 * containing a list of words (nodes) and connections (edges) between them that together
 * form the AMR graph for that sentence.
 *
 * Extends {@link Sentence}, which presumably provides the underlying `words` list
 * and other shared sentence-level behavior.
 */
public class AmrSentence extends Sentence {
    private final Map<String, Integer> variableCounts = new HashMap<>();
    private final Map<AmrWord, String> wordVariables = new IdentityHashMap<>();
    private final Set<AmrWord> renderedWords = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final String MISSING_RELATION = "UNKNOWN_REL";   // renders as :unknown
    private static final String MISSING_CONCEPT  = "MISSING_CONCEPT";
    private static final String ROOT_CONCEPT = "ROOT";
    private String rootRelation = null;

    // The list of edges (relations) between words in this sentence's AMR graph.
    // Each connection has a "from" word, a "to" word, and a relation label ("with").
    private ArrayList<AmrConnection> connections;

    // Metadata describing which file (and which sentence within a corpus of files)
    // this AmrSentence corresponds to. Immutable once set.
    private final FileDescription fileDescription;

    private String sentence = "";

    private Set<AmrWord> consumedWords = Collections.newSetFromMap(new IdentityHashMap<>());

    private List<AmrWord> allWords = new ArrayList<>();
    private AmrWord rootWord;

    /**
     * Convenience constructor: builds a FileDescription from a path + file name,
     * then delegates to the main constructor.
     */
    public AmrSentence(String path, String fileName){
        this(new FileDescription(path, fileName));
    }

    /**
     * Main constructor. Stores the file description, initializes an empty
     * connections list, and immediately loads the sentence's words/connections
     * from the underlying XML file via reload().
     */
    public AmrSentence(FileDescription fileDescription){
        this.fileDescription = fileDescription;
        connections = new ArrayList<>();
        reload();
    }

    public String getSentence(){
        return sentence;
    }

    public void setSentence(String sentence){
        this.sentence = sentence;
    }

    /**
     * (Re)parses the XML file pointed to by fileDescription and rebuilds this
     * sentence's words and connections from its contents. Parsing errors
     * (malformed XML, I/O issues) are silently ignored, in which case the
     * sentence's content will simply not be (re)loaded.
     */
    public void reload(){
        DOMParser parser = new DOMParser();
        Document doc;
        try {
            parser.parse(fileDescription.getFileName());
        } catch (SAXException | IOException ignored) {
            // Parsing failed silently; doc stays whatever the parser last produced (likely null/stale).
        }
        doc = parser.getDocument();
        if (doc != null) {
            // Start parsing from the root element's first child (skip the document node itself).
            loadFromXml(doc.getDocumentElement());
        }
    }

    /** Returns the original/raw file name (e.g., without resolved path), as given by FileDescription. */
    public String getRawFileName(){
        return fileDescription.getRawFileName();
    }

    /** Returns the full file name/path of the XML file backing this sentence. */
    public String getFileName(){
        return fileDescription.getFileName();
    }

    /**
     * Returns the folder portion of the file path, i.e. everything from the last
     * "/" onward (including the slash itself, since substring starts at lastIndexOf).
     */
    public String getFolder(){
        return fileDescription.getPath().substring(fileDescription.getPath().lastIndexOf("/"));
    }

    /** Returns the FileDescription object backing this sentence (path, file name, index info, etc.). */
    public FileDescription getFileDescription(){
        return fileDescription;
    }

    /**
     * Looks up a word in this sentence's word list by its name (the unique node ID
     * used in the AMR XML, e.g. "w1", "w2", ...). Returns null if not found.
     * Note: relies on `words` field inherited from Sentence; casts to AmrWord since
     * this subclass always stores AmrWord instances.
     */
    private AmrWord getWord(String name) {
        for (Word word : words) {
            if (word.getName().equals(name)){
                return (AmrWord) word;
            }
        }
        return null;
    }

    /** Returns the connection (edge) at the given index in the connections list. */
    public AmrConnection getConnection(int index){
        return connections.get(index);
    }

    /** Returns the total number of connections (edges) in this sentence's AMR graph. */
    public int connectionCount(){
        return connections.size();
    }

    /**
     * Creates a new AmrConnection between two words with the given relation label
     * and appends it to this sentence's connections list.
     */
    public void addConnection(AmrWord from, AmrWord to, String with){
        AmrConnection connection = new AmrConnection(from, to, with);
        connections.add(connection);
    }

    /**
     * Parses the XML tree (starting at rootNode, expected to be the <SENTENCE> or
     * similar root element) and populates `words` and `connections` accordingly.
     *
     * Expected XML structure: a sequence of sibling elements, each either:
     *  - <Word name="..." positionX="..." positionY="..."/>  -> creates an AmrWord
     *  - <Connection from="..." to="..." with="..."/>        -> creates an AmrConnection
     *    (the "with" attribute, i.e. relation label, is optional; defaults to "")
     *
     * Words must be parsed before the connections that reference them, since
     * getWord() is used to resolve "from"/"to" names to AmrWord objects; connections
     * referencing unknown words (from or to null) are silently skipped.
     */
    private void loadFromXml(Node rootNode) {
        Node objectNode;
        NamedNodeMap attributes;
        String objectName;
        AmrWord from, to;
        String with;

        // Clear any previously loaded words before re-populating (e.g. on reload()).
        words.clear();
        connections.clear();

        if (rootNode.hasAttributes() && rootNode.getAttributes().getNamedItem("sentence") != null){
            sentence = rootNode.getAttributes().getNamedItem("sentence").getNodeValue();
        }

        // Iterate over each child element of the root node in document order.
        objectNode = rootNode.getFirstChild();
        while (objectNode != null) {
            if (objectNode.hasAttributes()) {
                objectName = objectNode.getNodeName();
                attributes = objectNode.getAttributes();

                if (objectName.equalsIgnoreCase("Word")) {
                    // Build an AmrWord from its name and (x, y) display position, then store it.
                    AmrWord word = new AmrWord(attributes.getNamedItem("name").getNodeValue(), new Point(Integer.parseInt(attributes.getNamedItem("positionX").getNodeValue()), Integer.parseInt(attributes.getNamedItem("positionY").getNodeValue())));
                    addWord(word);
                } else {
                    if (objectName.equalsIgnoreCase("Connection")) {
                        // Resolve the endpoint words by name; these must already exist
                        // in `words` (i.e. their <Word> elements appeared earlier in the XML).
                        from = getWord(attributes.getNamedItem("from").getNodeValue());
                        to = getWord(attributes.getNamedItem("to").getNodeValue());

                        // "with" (the relation/role label, e.g. ":ARG0") is optional.
                        if (attributes.getNamedItem("with") != null) {
                            with = attributes.getNamedItem("with").getNodeValue();
                        } else {
                            with = "";
                        }

                        // Only add the connection if both endpoints were successfully resolved.
                        if (from != null && to != null){
                            addConnection(from, to, with);
                        }
                    }
                    // Any other element name (without the "Word"/"Connection" match) is ignored.
                }
            }
            objectNode = objectNode.getNextSibling();
        }
    }

    /**
     * Moves this sentence's pointer backward by `count` sentences within the
     * corpus (if a previous file exists at that offset), then reloads the
     * AmrSentence content (words/connections) from the newly-pointed-to file.
     */
    public void previousSentence(int count){
        if (fileDescription.previousFileExists(count)){
            fileDescription.addToIndex(-count);
            connections = new ArrayList<>();
            reload();
        }
    }

    /**
     * Moves this sentence's pointer forward by `count` sentences within the
     * corpus (if a next file exists at that offset), then reloads the
     * AmrSentence content (words/connections) from the newly-pointed-to file.
     */
    public void nextSentence(int count){
        if (fileDescription.nextFileExists(count)){
            fileDescription.addToIndex(count);
            connections = new ArrayList<>();
            reload();
        }
    }

    /**
     * Picks the root deterministically: among words that are never a connection target,
     * take the one whose subtree is largest (ties: earliest in the XML). If every word
     * has a parent (cycle), apply the same rule to all words.
     */
    private AmrWord getRoot() {
        allWords = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            allWords.add((AmrWord) getWord(i));
        }
        if (allWords.isEmpty()) {
            return null;
        }

        Set<AmrWord> hasParent = parentedWords();

        AmrWord best = null;
        int bestSize = -1;
        for (AmrWord w : allWords) {
            if (hasParent.contains(w)) continue;
            int size = reachableCount(w);
            if (size > bestSize) {
                best = w;
                bestSize = size;
            }
        }
        if (best != null) {
            return best;
        }

        // No parentless word (cyclic graph): fall back to the word reaching the most nodes.
        for (AmrWord w : allWords) {
            int size = reachableCount(w);
            if (size > bestSize) {
                best = w;
                bestSize = size;
            }
        }
        return best;
    }

    private Set<AmrWord> parentedWords() {
        Set<AmrWord> hasParent = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AmrConnection connection : connections) {
            hasParent.add(connection.to());
        }
        return hasParent;
    }

    /** Number of words reachable from `start` (including itself). */
    private int reachableCount(AmrWord start) {
        Set<AmrWord> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<AmrWord> stack = new ArrayDeque<>();
        seen.add(start);
        stack.push(start);
        while (!stack.isEmpty()) {
            AmrWord current = stack.pop();
            for (AmrConnection connection : connections) {
                if (connection.from() == current && seen.add(connection.to())) {
                    stack.push(connection.to());
                }
            }
        }
        return seen.size();
    }

    /** Guarantees a valid single-token relation name; empty/"null" becomes the placeholder. */
    private String cleanRelation(String relation) {
        if (relation == null) return MISSING_RELATION;
        String r = relation.trim();
        while (r.startsWith(":")) {
            r = r.substring(1);
        }
        r = r.replaceAll("\\s+", "-");
        if (r.isEmpty() || r.equalsIgnoreCase("null")) return MISSING_RELATION;
        return r;
    }

    private String indent(int tabs) {
        StringBuilder sb = new StringBuilder("\n");
        for (int i = 0; i < tabs; i++) {
            sb.append('\t');
        }
        return sb.toString();
    }

    /**
     * Called once, at the end of the root's rendering. Attaches every node that the root
     * traversal never reached, using the placeholder relation, so nothing is lost and the
     * output stays a single tree. Pass 1 handles parentless nodes (e.g. from="null" targets),
     * pass 2 handles anything left over (e.g. unreachable cycles).
     */
    private String attachLeftovers(int tabCount) {
        StringBuilder sb = new StringBuilder();
        Set<AmrWord> hasParent = parentedWords();

        for (int pass = 1; pass <= 2; pass++) {
            for (AmrWord w : allWords) {
                if (renderedWords.contains(w) || consumedWords.contains(w)) continue;
                if (pass == 1 && hasParent.contains(w)) continue;

                sb.append(indent(tabCount + 1))
                        .append(":").append(MISSING_RELATION).append(" ")
                        .append(wordToString(w, tabCount + 1));
            }
        }
        return sb.toString();
    }

    private String normalizeMultiword(String instance) {

        String[] tokens = instance.trim().split("\\s+");

        if (tokens.length <= 1) {
            return instance;
        }

        // First token: e.g. "2/hava"
        String[] first = tokens[0].split("/", 2);

        if (first.length != 2) {
            // No index on the first token; just join with "-"
            return String.join("-", tokens);
        }

        String index = first[0];
        StringBuilder concept = new StringBuilder(first[1]);

        // Add the remaining concepts, ignoring their indices
        for (int i = 1; i < tokens.length; i++) {

            String token = tokens[i];

            if (token.contains("/")) {
                String[] parts = token.split("/", 2);
                concept.append("-").append(parts[1]);
            } else {
                concept.append("-").append(token);
            }
        }

        return index + "/" + concept;
    }
    /**
     * Recursively builds the parenthesized AMR string representation rooted at `word`,
     * e.g. "(w1 :ARG0 (w2) :ARG1 (w3 :mod (w4)))". `tabCount` tracks current nesting
     * depth so that each child relation is indented with one extra tab beyond its parent,
     * for readability when printed.
     */
    private String wordToString(AmrWord word, int tabCount) {

        String instance = word.getName() == null ? "" : normalizeMultiword(word.getName());
        instance = instance.replaceAll("-?\\(\\d+\\)\\s*$", "");   // person-(2) -> person

        if (word == rootWord) {
            int colon = instance.indexOf(':');
            if (colon > 0 && colon < instance.length() - 1) {
                rootRelation = instance.substring(colon + 1);
                instance = instance.substring(0, colon);
            }
        }

        if (instance.trim().isEmpty()) {
            instance = MISSING_CONCEPT;
        }

        // AMR constants
        if (instance.equals("-") || instance.equals("imperative")) {
            renderedWords.add(word);
            return instance;
        }

        // Reentrant reference: return only the variable.
        if (renderedWords.contains(word)) {
            return wordVariables.get(word);
        }

        String variable;

        if (instance.contains("/")) {
            String[] parts = instance.split("/", 2);
            variable = parts[0];
        } else {
            String base = instance.substring(0, 1);
            int count = variableCounts.getOrDefault(base, 0) + 1;
            variableCounts.put(base, count);
            variable = base + count;
            instance = variable + "/" + instance;
        }

        wordVariables.put(word, variable);
        renderedWords.add(word);

        StringBuilder result = new StringBuilder("(" + instance);

        for (AmrConnection connection : connections) {

            if (connection.from() != word) continue;

            String relation = cleanRelation(connection.with());

            result.append(indent(tabCount + 1)).append(":").append(relation);

            if (relation.equals("wiki")) {
                result.append(" -");
                consumedWords.add(connection.to());
            } else if (relation.equals("polarity") && !connection.to().getName().contains("amr-unknown")){
                result.append(" -");
                consumedWords.add(connection.to());
            }
            else {
                result.append(" ").append(wordToString(connection.to(), tabCount + 1));
            }
        }

        if (word == rootWord) {
            result.append(attachLeftovers(tabCount));
        }

        return result.append(")").toString();
    }

    /**
     * Returns a human-readable representation of this sentence: the file name,
     * followed by the full AMR graph rendered as an indented parenthesized
     * expression starting from the root word.
     */
    @Override
    public String toString() {

        variableCounts.clear();
        wordVariables.clear();
        renderedWords.clear();
        consumedWords.clear();
        rootRelation = null;

        rootWord = getRoot();

        String graph;
        if (rootWord == null) {
            graph = "(m1/" + MISSING_CONCEPT + ")";            // empty file: still parsable
        } else {
            // NEW: if the root name carries a relation, render one level deeper and wrap it
            boolean hasColon = rootWord.getName() != null && rootWord.getName().contains(":");
            String body = wordToString(rootWord, hasColon ? 1 : 0);

            if (rootRelation != null) {
                graph = "(root/" + ROOT_CONCEPT
                        + indent(1) + ":" + cleanRelation(rootRelation)
                        + " " + body + ")";
            } else {
                graph = body;
            }
        }

        return "# ::id " + getRawFileName() + "\n" +
                "# ::snt " + sentence + "\n" +
                graph;
    }

}