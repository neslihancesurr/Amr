import Amr.Corpus.AmrSentence;
import org.junit.Test;

public class TestPenman {

    @Test
    public void test() {
        AmrSentence sentence = new AmrSentence("amrsentences", "0000.test");
        System.out.println(sentence);
        sentence = new AmrSentence("amrsentences", "0001.test");
        System.out.println(sentence);
        sentence = new AmrSentence("amrsentences", "0002.test");
        System.out.println(sentence);
        sentence = new AmrSentence("amrsentences", "0003.test");
        System.out.println(sentence);
        sentence = new AmrSentence("amrsentences", "0004.test");
        System.out.println(sentence);
        sentence = new AmrSentence("amrsentences", "0005.test");
        System.out.println(sentence);
        sentence = new AmrSentence("amrsentences", "0006.test");
        System.out.println(sentence);
        sentence = new AmrSentence("amrsentences", "0007.test");
        System.out.println(sentence);
        sentence = new AmrSentence("amrsentences", "0008.test");
        System.out.println(sentence);
        sentence = new AmrSentence("amrsentences", "0009.test");
        System.out.println(sentence);
        sentence = new AmrSentence("amrsentences", "0010.test");
        System.out.println(sentence);
    }

    @Test
    public void test2() {
        AmrSentence sentence = new AmrSentence("src/gb_xml_parser_outputs", "0000.xml");
        System.out.println(sentence);
    }
}
